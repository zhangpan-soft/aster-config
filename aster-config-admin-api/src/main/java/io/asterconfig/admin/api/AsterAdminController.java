package io.asterconfig.admin.api;

import io.asterconfig.codec.ConfigCodecRegistry;
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
import io.asterconfig.core.model.ValueType;
import io.asterconfig.core.service.ConfigManagementService;
import io.asterconfig.core.spi.EmbedTokenValidator;
import io.asterconfig.core.spi.UserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/aster/admin/api")
public class AsterAdminController {

    private final ConfigManagementService configService;
    private final ConfigCodecRegistry codecRegistry;
    private final UserProvider userProvider;
    private final EmbedTokenValidator embedTokenValidator;

    public AsterAdminController(
            ConfigManagementService configService,
            ConfigCodecRegistry codecRegistry,
            UserProvider userProvider,
            EmbedTokenValidator embedTokenValidator
    ) {
        this.configService = configService;
        this.codecRegistry = codecRegistry;
        this.userProvider = userProvider;
        this.embedTokenValidator = embedTokenValidator;
    }

    @GetMapping("/items")
    public List<ConfigItem> items(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDisabled
    ) {
        ConfigScope scope = new ConfigScope(env, namespace);
        return configService.listItems(new ConfigQuery(scope, keyword, includeDisabled));
    }

    @GetMapping("/drafts")
    public List<ConfigDraft> drafts(
            @RequestParam String env,
            @RequestParam String namespace
    ) {
        return configService.listDrafts(new ConfigScope(env, namespace));
    }

    @PostMapping("/drafts")
    public ConfigDraft saveDraft(@RequestBody DraftRequest request) {
        Instant now = Instant.now();
        ConfigScope scope = new ConfigScope(request.env(), request.namespace());
        ConfigDraft draft = new ConfigDraft(
                request.id() == null ? UUID.randomUUID().toString() : request.id(),
                request.itemId(),
                request.operationType(),
                scope,
                request.key(),
                request.value(),
                request.valueType() == null ? ValueType.infer(request.value()) : request.valueType(),
                request.sourceFormat() == null ? SourceFormat.KEY_VALUE : request.sourceFormat(),
                request.encrypted(),
                request.enabled(),
                request.description(),
                DraftStatus.OPEN,
                currentUser(),
                now,
                now
        );
        return configService.saveDraft(draft);
    }

    @PostMapping("/documents/parse")
    public DocumentParseResponse parseDocument(@RequestBody DocumentRequest request) {
        Map<String, ConfigValue> values = codecRegistry.flatten(request.sourceFormat(), request.content());
        return new DocumentParseResponse(request.sourceFormat(), values.size(), values);
    }

    @PostMapping("/documents/draft")
    public DocumentDraftResponse saveDocumentDraft(
            @RequestBody DocumentRequest request,
            @RequestHeader(name = "X-Aster-Embed-Token", required = false) String embedToken
    ) {
        ConfigScope scope = new ConfigScope(request.env(), request.namespace());
        requireEmbedPermission(embedToken, scope, "config:draft");
        Map<String, ConfigValue> values = codecRegistry.flatten(request.sourceFormat(), request.content());
        var document = configService.saveDocumentDraft(scope, request.sourceFormat(), request.content(), values, currentUser());
        return new DocumentDraftResponse(document.revision(), values.size(), configService.listDrafts(scope).size());
    }

    @PostMapping("/releases/publish")
    public PublishResult publish(
            @RequestBody PublishConfigRequest request,
            @RequestHeader(name = "X-Aster-Embed-Token", required = false) String embedToken
    ) {
        ConfigScope scope = new ConfigScope(request.env(), request.namespace());
        requireEmbedPermission(embedToken, scope, "config:publish");
        return configService.publish(new PublishRequest(scope, request.releaseNote(), currentUser()));
    }

    @GetMapping("/releases")
    public List<ReleaseRecord> releases(
            @RequestParam String env,
            @RequestParam String namespace,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return configService.listReleases(new ConfigScope(env, namespace), limit);
    }

    private void requireEmbedPermission(String token, ConfigScope scope, String action) {
        if (!embedTokenValidator.validate(token, scope, action)) {
            throw new IllegalArgumentException("Invalid embed token");
        }
    }

    private String currentUser() {
        return userProvider.currentUser().userId();
    }

    public record DocumentRequest(
            String env,
            String namespace,
            SourceFormat sourceFormat,
            String content
    ) {
    }

    public record DocumentParseResponse(
            SourceFormat sourceFormat,
            int itemCount,
            Map<String, ConfigValue> values
    ) {
    }

    public record DocumentDraftResponse(
            long baseRevision,
            int parsedItems,
            int openDrafts
    ) {
    }

    public record DraftRequest(
            String id,
            String itemId,
            OperationType operationType,
            String env,
            String namespace,
            String key,
            String value,
            ValueType valueType,
            SourceFormat sourceFormat,
            boolean encrypted,
            boolean enabled,
            String description
    ) {
    }

    public record PublishConfigRequest(
            String env,
            String namespace,
            String releaseNote
    ) {
    }
}
