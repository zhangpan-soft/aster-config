package io.asterconfig.store.jdbc;

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
import io.asterconfig.core.model.ValueType;
import io.asterconfig.core.spi.ConfigStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class JdbcConfigStore implements ConfigStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    public JdbcConfigStore(DataSource dataSource, TransactionTemplate transactionTemplate) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public List<ConfigItem> listItems(ConfigQuery query) {
        StringBuilder sql = new StringBuilder("""
                select * from aster_config_item
                where env = :env and namespace = :namespace
                """);
        MapSqlParameterSource params = scopeParams(query.scope());
        if (!query.includeDisabled()) {
            sql.append(" and enabled = true");
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" and (config_key like :keyword or config_value like :keyword or description like :keyword)");
            params.addValue("keyword", "%" + query.keyword().trim() + "%");
        }
        sql.append(" order by config_key asc");
        return jdbc.query(sql.toString(), params, itemMapper());
    }

    @Override
    public List<ConfigDraft> listDrafts(ConfigScope scope) {
        return jdbc.query("""
                select * from aster_config_draft
                where env = :env and namespace = :namespace and status = 'OPEN'
                order by updated_at desc
                """, scopeParams(scope), draftMapper());
    }

    @Override
    public ConfigDraft saveDraft(ConfigDraft draft) {
        String id = draft.id() == null || draft.id().isBlank() ? UUID.randomUUID().toString() : draft.id();
        ConfigDraft normalized = new ConfigDraft(id, draft.itemId(), draft.operationType(), draft.scope(), draft.key(),
                draft.value(), draft.valueType(), draft.sourceFormat(), draft.encrypted(), draft.enabled(),
                draft.description(), draft.status(), draft.operator(), draft.createdAt(), draft.updatedAt());
        jdbc.update("""
                delete from aster_config_draft
                where env = :env and namespace = :namespace and config_key = :key and status = 'OPEN'
                """, scopeParams(normalized.scope()).addValue("key", normalized.key()));
        jdbc.update("""
                insert into aster_config_draft
                  (id, item_id, operation_type, env, namespace, config_key, config_value, value_type,
                   source_format, encrypted, enabled, description, status, operator, created_at, updated_at)
                values
                  (:id, :itemId, :operationType, :env, :namespace, :key, :value, :valueType,
                   :sourceFormat, :encrypted, :enabled, :description, :status, :operator, :createdAt, :updatedAt)
                """, draftParams(normalized));
        return normalized;
    }

    @Override
    public void saveDrafts(List<ConfigDraft> drafts) {
        drafts.forEach(this::saveDraft);
    }

    @Override
    public void discardDraft(String draftId) {
        jdbc.update("delete from aster_config_draft where id = :id", new MapSqlParameterSource("id", draftId));
    }

    @Override
    public ConfigDocument saveDocument(ConfigDocument document, List<ConfigDraft> generatedDrafts) {
        transactionTemplate.executeWithoutResult(status -> {
            int updated = jdbc.update("""
                    update aster_config_document
                    set source_content = :sourceContent, revision = :revision, operator = :operator, updated_at = :updatedAt
                    where env = :env and namespace = :namespace and source_format = :sourceFormat
                    """, documentParams(document));
            if (updated == 0) {
                jdbc.update("""
                        insert into aster_config_document
                          (env, namespace, source_format, source_content, revision, operator, updated_at)
                        values
                          (:env, :namespace, :sourceFormat, :sourceContent, :revision, :operator, :updatedAt)
                        """, documentParams(document));
            }
            saveDrafts(generatedDrafts);
        });
        return document;
    }

    @Override
    public Optional<ConfigDocument> getDocument(ConfigScope scope, SourceFormat sourceFormat) {
        List<ConfigDocument> documents = jdbc.query("""
                select * from aster_config_document
                where env = :env and namespace = :namespace and source_format = :sourceFormat
                """, scopeParams(scope).addValue("sourceFormat", sourceFormat.name()), documentMapper());
        return documents.stream().findFirst();
    }

    @Override
    public PublishResult publish(PublishRequest request) {
        return transactionTemplate.execute(status -> {
            List<ConfigDraft> drafts = listDrafts(request.scope());
            Instant now = Instant.now();
            if (drafts.isEmpty()) {
                return new PublishResult(request.scope(), currentRevision(request.scope()), 0, now);
            }
            long nextRevision = currentRevision(request.scope()) + 1;
            for (ConfigDraft draft : drafts) {
                applyDraft(draft, nextRevision, now);
            }
            jdbc.update("""
                    update aster_config_draft
                    set status = 'PUBLISHED', updated_at = :updatedAt
                    where env = :env and namespace = :namespace and status = 'OPEN'
                    """, scopeParams(request.scope()).addValue("updatedAt", Timestamp.from(now)));
            ReleaseRecord release = new ReleaseRecord(UUID.randomUUID().toString(), request.scope(), nextRevision,
                    request.releaseNote(), request.operator(), now);
            jdbc.update("""
                    insert into aster_config_release
                      (id, env, namespace, revision, release_note, operator, published_at)
                    values
                      (:id, :env, :namespace, :revision, :releaseNote, :operator, :publishedAt)
                    """, releaseParams(release));
            jdbc.update("""
                    insert into aster_config_publish_event
                      (id, env, namespace, revision, source_node_id, created_at)
                    values
                      (:id, :env, :namespace, :revision, null, :createdAt)
                    """, scopeParams(request.scope())
                    .addValue("id", UUID.randomUUID().toString())
                    .addValue("revision", nextRevision)
                    .addValue("createdAt", Timestamp.from(now)));
            return new PublishResult(request.scope(), nextRevision, drafts.size(), now);
        });
    }

    @Override
    public List<ReleaseRecord> listReleases(ConfigScope scope, int limit) {
        return jdbc.query("""
                select * from aster_config_release
                where env = :env and namespace = :namespace
                order by revision desc
                limit :limit
                """, scopeParams(scope).addValue("limit", limit <= 0 ? 20 : limit), releaseMapper());
    }

    @Override
    public long currentRevision(ConfigScope scope) {
        Long revision = jdbc.queryForObject("""
                select coalesce(max(revision), 0) from aster_config_release
                where env = :env and namespace = :namespace
                """, scopeParams(scope), Long.class);
        return revision == null ? 0 : revision;
    }

    private void applyDraft(ConfigDraft draft, long revision, Instant now) {
        if (draft.operationType() == OperationType.DELETE) {
            jdbc.update("""
                    delete from aster_config_item
                    where (id = :itemId and :itemId is not null)
                       or (env = :env and namespace = :namespace and config_key = :key)
                    """, draftParams(draft));
            return;
        }
        String itemId = draft.itemId() == null || draft.itemId().isBlank() ? UUID.randomUUID().toString() : draft.itemId();
        int updated = jdbc.update("""
                update aster_config_item
                set config_value = :value, value_type = :valueType, source_format = :sourceFormat,
                    encrypted = :encrypted, enabled = :enabled, description = :description,
                    revision = :revision, updated_at = :updatedAt
                where id = :id
                   or (env = :env and namespace = :namespace and config_key = :key)
                """, draftParams(draft)
                .addValue("id", itemId)
                .addValue("revision", revision)
                .addValue("updatedAt", Timestamp.from(now)));
        if (updated == 0) {
            jdbc.update("""
                    insert into aster_config_item
                      (id, env, namespace, config_key, config_value, value_type, source_format,
                       encrypted, enabled, description, revision, created_at, updated_at)
                    values
                      (:id, :env, :namespace, :key, :value, :valueType, :sourceFormat,
                       :encrypted, :enabled, :description, :revision, :createdAt, :updatedAt)
                    """, draftParams(draft)
                    .addValue("id", itemId)
                    .addValue("revision", revision)
                    .addValue("createdAt", Timestamp.from(now))
                    .addValue("updatedAt", Timestamp.from(now)));
        }
    }

    private MapSqlParameterSource scopeParams(ConfigScope scope) {
        return new MapSqlParameterSource()
                .addValue("env", scope.env())
                .addValue("namespace", scope.namespace());
    }

    private MapSqlParameterSource draftParams(ConfigDraft draft) {
        return scopeParams(draft.scope())
                .addValue("id", draft.id())
                .addValue("itemId", draft.itemId())
                .addValue("operationType", draft.operationType().name())
                .addValue("key", draft.key())
                .addValue("value", draft.value())
                .addValue("valueType", draft.valueType().name())
                .addValue("sourceFormat", draft.sourceFormat().name())
                .addValue("encrypted", draft.encrypted())
                .addValue("enabled", draft.enabled())
                .addValue("description", draft.description())
                .addValue("status", draft.status().name())
                .addValue("operator", draft.operator())
                .addValue("createdAt", Timestamp.from(draft.createdAt()))
                .addValue("updatedAt", Timestamp.from(draft.updatedAt()));
    }

    private MapSqlParameterSource documentParams(ConfigDocument document) {
        return scopeParams(document.scope())
                .addValue("sourceFormat", document.sourceFormat().name())
                .addValue("sourceContent", document.sourceContent())
                .addValue("revision", document.revision())
                .addValue("operator", document.operator())
                .addValue("updatedAt", Timestamp.from(document.updatedAt()));
    }

    private MapSqlParameterSource releaseParams(ReleaseRecord release) {
        return scopeParams(release.scope())
                .addValue("id", release.id())
                .addValue("revision", release.revision())
                .addValue("releaseNote", release.releaseNote())
                .addValue("operator", release.operator())
                .addValue("publishedAt", Timestamp.from(release.publishedAt()));
    }

    private RowMapper<ConfigItem> itemMapper() {
        return (rs, rowNum) -> new ConfigItem(
                rs.getString("id"),
                scope(rs),
                rs.getString("config_key"),
                rs.getString("config_value"),
                ValueType.valueOf(rs.getString("value_type")),
                SourceFormat.valueOf(rs.getString("source_format")),
                rs.getBoolean("encrypted"),
                rs.getBoolean("enabled"),
                rs.getString("description"),
                rs.getLong("revision"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private RowMapper<ConfigDraft> draftMapper() {
        return (rs, rowNum) -> new ConfigDraft(
                rs.getString("id"),
                rs.getString("item_id"),
                OperationType.valueOf(rs.getString("operation_type")),
                scope(rs),
                rs.getString("config_key"),
                rs.getString("config_value"),
                ValueType.valueOf(rs.getString("value_type")),
                SourceFormat.valueOf(rs.getString("source_format")),
                rs.getBoolean("encrypted"),
                rs.getBoolean("enabled"),
                rs.getString("description"),
                DraftStatus.valueOf(rs.getString("status")),
                rs.getString("operator"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private RowMapper<ConfigDocument> documentMapper() {
        return (rs, rowNum) -> new ConfigDocument(
                scope(rs),
                SourceFormat.valueOf(rs.getString("source_format")),
                rs.getString("source_content"),
                rs.getLong("revision"),
                rs.getString("operator"),
                instant(rs, "updated_at")
        );
    }

    private RowMapper<ReleaseRecord> releaseMapper() {
        return (rs, rowNum) -> new ReleaseRecord(
                rs.getString("id"),
                scope(rs),
                rs.getLong("revision"),
                rs.getString("release_note"),
                rs.getString("operator"),
                instant(rs, "published_at")
        );
    }

    private ConfigScope scope(ResultSet rs) throws SQLException {
        return new ConfigScope(rs.getString("env"), rs.getString("namespace"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
