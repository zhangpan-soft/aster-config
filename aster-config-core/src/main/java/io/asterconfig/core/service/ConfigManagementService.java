package io.asterconfig.core.service;

import io.asterconfig.core.model.ConfigDocument;
import io.asterconfig.core.model.ConfigDraft;
import io.asterconfig.core.model.ConfigItem;
import io.asterconfig.core.model.ConfigQuery;
import io.asterconfig.core.model.ConfigScope;
import io.asterconfig.core.model.ConfigValue;
import io.asterconfig.core.model.DraftStatus;
import io.asterconfig.core.model.OperationType;
import io.asterconfig.core.model.PublishRequest;
import io.asterconfig.core.model.PublishResult;
import io.asterconfig.core.model.ReleaseRecord;
import io.asterconfig.core.model.SourceFormat;
import io.asterconfig.core.spi.ConfigStore;
import io.asterconfig.core.spi.ConfigPublishListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfigManagementService {

    private final ConfigStore configStore;
    private final List<ConfigPublishListener> publishListeners;

    public ConfigManagementService(ConfigStore configStore) {
        this(configStore, List.of());
    }

    public ConfigManagementService(ConfigStore configStore, List<ConfigPublishListener> publishListeners) {
        this.configStore = configStore;
        this.publishListeners = publishListeners == null ? List.of() : List.copyOf(publishListeners);
    }

    public List<ConfigItem> listItems(ConfigQuery query) {
        return configStore.listItems(query);
    }

    public List<ConfigDraft> listDrafts(ConfigScope scope) {
        return configStore.listDrafts(scope);
    }

    public ConfigDraft saveDraft(ConfigDraft draft) {
        return configStore.saveDraft(normalizeDraft(draft));
    }

    public ConfigDocument saveDocumentDraft(
            ConfigScope scope,
            SourceFormat sourceFormat,
            String sourceContent,
            Map<String, ConfigValue> flattenedValues,
            String operator
    ) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(sourceFormat, "sourceFormat must not be null");
        Objects.requireNonNull(flattenedValues, "flattenedValues must not be null");

        Instant now = Instant.now();
        Map<String, ConfigItem> currentItems = configStore.listItems(ConfigQuery.scope(scope)).stream()
                .collect(Collectors.toMap(ConfigItem::key, Function.identity(), (left, right) -> right, TreeMap::new));
        Map<String, ConfigValue> nextItems = new TreeMap<>(flattenedValues);
        List<ConfigDraft> drafts = new ArrayList<>();

        nextItems.forEach((key, value) -> {
            ConfigItem existing = currentItems.get(key);
            if (existing == null) {
                drafts.add(new ConfigDraft(UUID.randomUUID().toString(), null, OperationType.ADD, scope, key,
                        value.value(), value.valueType(), sourceFormat, false, true, null, DraftStatus.OPEN,
                        operator, now, now));
                return;
            }
            if (!Objects.equals(existing.value(), value.value()) || existing.valueType() != value.valueType()) {
                drafts.add(new ConfigDraft(UUID.randomUUID().toString(), existing.id(), OperationType.UPDATE, scope, key,
                        value.value(), value.valueType(), sourceFormat, existing.encrypted(), existing.enabled(),
                        existing.description(), DraftStatus.OPEN, operator, now, now));
            }
        });

        currentItems.values().stream()
                .filter(item -> !nextItems.containsKey(item.key()))
                .forEach(item -> drafts.add(new ConfigDraft(UUID.randomUUID().toString(), item.id(), OperationType.DELETE,
                        scope, item.key(), item.value(), item.valueType(), sourceFormat, item.encrypted(), item.enabled(),
                        item.description(), DraftStatus.OPEN, operator, now, now)));

        ConfigDocument document = new ConfigDocument(scope, sourceFormat, sourceContent,
                configStore.currentRevision(scope), operator, now);
        return configStore.saveDocument(document, drafts);
    }

    public Optional<ConfigDocument> getDocument(ConfigScope scope, SourceFormat sourceFormat) {
        return configStore.getDocument(scope, sourceFormat);
    }

    public PublishResult publish(PublishRequest request) {
        PublishResult result = configStore.publish(request);
        if (result.changedItems() > 0) {
            publishListeners.forEach(listener -> listener.onPublish(result));
        }
        return result;
    }

    public List<ReleaseRecord> listReleases(ConfigScope scope, int limit) {
        return configStore.listReleases(scope, limit);
    }

    private ConfigDraft normalizeDraft(ConfigDraft draft) {
        Instant now = Instant.now();
        String id = draft.id() == null || draft.id().isBlank() ? UUID.randomUUID().toString() : draft.id();
        return new ConfigDraft(
                id,
                draft.itemId(),
                draft.operationType(),
                draft.scope(),
                draft.key(),
                draft.value(),
                draft.valueType(),
                draft.sourceFormat(),
                draft.encrypted(),
                draft.enabled(),
                draft.description(),
                draft.status() == null ? DraftStatus.OPEN : draft.status(),
                draft.operator(),
                draft.createdAt() == null ? now : draft.createdAt(),
                now
        );
    }

    public Map<String, String> toPropertyMap(ConfigScope scope) {
        return configStore.listItems(ConfigQuery.scope(scope)).stream()
                .filter(ConfigItem::enabled)
                .sorted(Comparator.comparing(ConfigItem::key))
                .collect(Collectors.toMap(ConfigItem::key, ConfigItem::value, (left, right) -> right, TreeMap::new));
    }
}
