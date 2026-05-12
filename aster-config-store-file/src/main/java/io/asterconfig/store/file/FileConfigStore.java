package io.asterconfig.store.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.asterconfig.core.model.ConfigDocument;
import io.asterconfig.core.model.ConfigDraft;
import io.asterconfig.core.model.ConfigItem;
import io.asterconfig.core.model.ConfigQuery;
import io.asterconfig.core.model.ConfigScope;
import io.asterconfig.core.model.DraftStatus;
import io.asterconfig.core.model.OperationType;
import io.asterconfig.core.model.PublishRequest;
import io.asterconfig.core.model.PublishResult;
import io.asterconfig.core.model.ReleaseRecord;
import io.asterconfig.core.model.SourceFormat;
import io.asterconfig.core.spi.ConfigStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class FileConfigStore implements ConfigStore {

    private final Path dataDir;
    private final ObjectMapper objectMapper;
    private final Path itemsFile;
    private final Path draftsFile;
    private final Path documentsFile;
    private final Path releasesFile;

    public FileConfigStore(Path dataDir) {
        this.dataDir = dataDir;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.itemsFile = dataDir.resolve("items.json");
        this.draftsFile = dataDir.resolve("drafts.json");
        this.documentsFile = dataDir.resolve("documents.json");
        this.releasesFile = dataDir.resolve("releases.json");
        ensureDataDir();
    }

    @Override
    public synchronized List<ConfigItem> listItems(ConfigQuery query) {
        Predicate<ConfigItem> predicate = item -> matchesScope(item.scope(), query.scope());
        if (!query.includeDisabled()) {
            predicate = predicate.and(ConfigItem::enabled);
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            predicate = predicate.and(item -> item.key().contains(keyword)
                    || (item.value() != null && item.value().contains(keyword))
                    || (item.description() != null && item.description().contains(keyword)));
        }
        return readItems().stream()
                .filter(predicate)
                .sorted(Comparator.comparing(ConfigItem::key))
                .toList();
    }

    @Override
    public synchronized List<ConfigDraft> listDrafts(ConfigScope scope) {
        return readDrafts().stream()
                .filter(draft -> draft.status() == DraftStatus.OPEN)
                .filter(draft -> matchesScope(draft.scope(), scope))
                .sorted(Comparator.comparing(ConfigDraft::updatedAt).reversed())
                .toList();
    }

    @Override
    public synchronized ConfigDraft saveDraft(ConfigDraft draft) {
        List<ConfigDraft> drafts = readDrafts();
        ConfigDraft normalized = draft.id() == null || draft.id().isBlank()
                ? withDraftId(draft, UUID.randomUUID().toString())
                : draft;
        drafts.removeIf(existing -> Objects.equals(existing.id(), normalized.id())
                || sameOpenDraft(existing, normalized));
        drafts.add(normalized);
        write(draftsFile, drafts);
        return normalized;
    }

    @Override
    public synchronized void saveDrafts(List<ConfigDraft> drafts) {
        drafts.forEach(this::saveDraft);
    }

    @Override
    public synchronized void discardDraft(String draftId) {
        List<ConfigDraft> drafts = readDrafts();
        drafts.removeIf(draft -> Objects.equals(draft.id(), draftId));
        write(draftsFile, drafts);
    }

    @Override
    public synchronized ConfigDocument saveDocument(ConfigDocument document, List<ConfigDraft> generatedDrafts) {
        List<ConfigDocument> documents = readDocuments();
        documents.removeIf(existing -> matchesScope(existing.scope(), document.scope())
                && existing.sourceFormat() == document.sourceFormat());
        documents.add(document);
        write(documentsFile, documents);
        saveDrafts(generatedDrafts);
        return document;
    }

    @Override
    public synchronized Optional<ConfigDocument> getDocument(ConfigScope scope, SourceFormat sourceFormat) {
        return readDocuments().stream()
                .filter(document -> matchesScope(document.scope(), scope))
                .filter(document -> document.sourceFormat() == sourceFormat)
                .findFirst();
    }

    @Override
    public synchronized PublishResult publish(PublishRequest request) {
        List<ConfigDraft> drafts = readDrafts();
        List<ConfigDraft> openDrafts = drafts.stream()
                .filter(draft -> draft.status() == DraftStatus.OPEN)
                .filter(draft -> matchesScope(draft.scope(), request.scope()))
                .toList();
        if (openDrafts.isEmpty()) {
            return new PublishResult(request.scope(), currentRevision(request.scope()), 0, Instant.now());
        }

        List<ConfigItem> items = new ArrayList<>(readItems());
        long nextRevision = currentRevision(request.scope()) + 1;
        Instant now = Instant.now();
        for (ConfigDraft draft : openDrafts) {
            applyDraft(items, draft, nextRevision, now);
        }

        drafts.removeIf(draft -> draft.status() == DraftStatus.OPEN && matchesScope(draft.scope(), request.scope()));
        write(itemsFile, items);
        write(draftsFile, drafts);

        List<ReleaseRecord> releases = new ArrayList<>(readReleases());
        releases.add(new ReleaseRecord(UUID.randomUUID().toString(), request.scope(), nextRevision,
                request.releaseNote(), request.operator(), now));
        write(releasesFile, releases);

        return new PublishResult(request.scope(), nextRevision, openDrafts.size(), now);
    }

    @Override
    public synchronized List<ReleaseRecord> listReleases(ConfigScope scope, int limit) {
        int normalizedLimit = limit <= 0 ? 20 : limit;
        return readReleases().stream()
                .filter(release -> matchesScope(release.scope(), scope))
                .sorted(Comparator.comparing(ReleaseRecord::revision).reversed())
                .limit(normalizedLimit)
                .toList();
    }

    @Override
    public synchronized long currentRevision(ConfigScope scope) {
        return readReleases().stream()
                .filter(release -> matchesScope(release.scope(), scope))
                .mapToLong(ReleaseRecord::revision)
                .max()
                .orElse(0);
    }

    private void applyDraft(List<ConfigItem> items, ConfigDraft draft, long revision, Instant now) {
        if (draft.operationType() == OperationType.DELETE) {
            items.removeIf(item -> matchesDraftTarget(item, draft));
            return;
        }

        Optional<ConfigItem> existing = items.stream().filter(item -> matchesDraftTarget(item, draft)).findFirst();
        String id = existing.map(ConfigItem::id).orElseGet(() -> UUID.randomUUID().toString());
        Instant createdAt = existing.map(ConfigItem::createdAt).orElse(now);
        items.removeIf(item -> matchesDraftTarget(item, draft));
        items.add(new ConfigItem(id, draft.scope(), draft.key(), draft.value(), draft.valueType(), draft.sourceFormat(),
                draft.encrypted(), draft.enabled(), draft.description(), revision, createdAt, now));
    }

    private boolean matchesDraftTarget(ConfigItem item, ConfigDraft draft) {
        return Objects.equals(item.id(), draft.itemId())
                || (matchesScope(item.scope(), draft.scope()) && Objects.equals(item.key(), draft.key()));
    }

    private boolean sameOpenDraft(ConfigDraft left, ConfigDraft right) {
        return left.status() == DraftStatus.OPEN
                && right.status() == DraftStatus.OPEN
                && matchesScope(left.scope(), right.scope())
                && Objects.equals(left.key(), right.key());
    }

    private ConfigDraft withDraftId(ConfigDraft draft, String id) {
        return new ConfigDraft(id, draft.itemId(), draft.operationType(), draft.scope(), draft.key(), draft.value(),
                draft.valueType(), draft.sourceFormat(), draft.encrypted(), draft.enabled(), draft.description(),
                draft.status(), draft.operator(), draft.createdAt(), draft.updatedAt());
    }

    private boolean matchesScope(ConfigScope left, ConfigScope right) {
        return Objects.equals(left.env(), right.env())
                && Objects.equals(left.namespace(), right.namespace());
    }

    private List<ConfigItem> readItems() {
        return read(itemsFile, new TypeReference<>() {
        });
    }

    private List<ConfigDraft> readDrafts() {
        return read(draftsFile, new TypeReference<>() {
        });
    }

    private List<ConfigDocument> readDocuments() {
        return read(documentsFile, new TypeReference<>() {
        });
    }

    private List<ReleaseRecord> readReleases() {
        return read(releasesFile, new TypeReference<>() {
        });
    }

    private <T> T read(Path file, TypeReference<T> typeReference) {
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return objectMapper.readValue("[]", typeReference);
            }
            return objectMapper.readValue(file.toFile(), typeReference);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }

    private void write(Path file, Object value) {
        try {
            Path tempFile = dataDir.resolve(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), value);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    private void ensureDataDir() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create data dir " + dataDir, e);
        }
    }
}
