package io.asterconfig.store.jdbc;

import io.asterconfig.core.model.ConfigDraft;
import io.asterconfig.core.model.ConfigItem;
import io.asterconfig.core.model.ConfigQuery;
import io.asterconfig.core.model.ConfigScope;
import io.asterconfig.core.model.DraftStatus;
import io.asterconfig.core.model.OperationType;
import io.asterconfig.core.model.PublishRequest;
import io.asterconfig.core.model.PublishResult;
import io.asterconfig.core.model.SourceFormat;
import io.asterconfig.core.model.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConfigStoreIT {

    private JdbcConfigStore store;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ByteArrayResource(JdbcSchema.MYSQL.getBytes(StandardCharsets.UTF_8)));
        populator.execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcConfigStore(dataSource,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        clearTables();
    }

    @Test
    void publishesDraftsAndWritesReleaseEvent() {
        ConfigScope scope = new ConfigScope("it", "common");
        Instant now = Instant.now();

        store.saveDraft(new ConfigDraft("draft-redis-host", null, OperationType.ADD, scope,
                "redis.host", "127.0.0.1", ValueType.STRING, SourceFormat.YAML, false, true,
                "redis host", DraftStatus.OPEN, "tester", now, now));
        store.saveDraft(new ConfigDraft("draft-redis-port", null, OperationType.ADD, scope,
                "redis.port", "6379", ValueType.NUMBER, SourceFormat.YAML, false, true,
                "redis port", DraftStatus.OPEN, "tester", now, now));

        PublishResult result = store.publish(new PublishRequest(scope, "integration test", "tester"));

        assertThat(result.revision()).isEqualTo(1);
        assertThat(result.changedItems()).isEqualTo(2);
        assertThat(store.currentRevision(scope)).isEqualTo(1);

        List<ConfigItem> items = store.listItems(ConfigQuery.scope(scope));
        assertThat(items).extracting(ConfigItem::key)
                .containsExactly("redis.host", "redis.port");
        assertThat(items).extracting(ConfigItem::revision)
                .containsOnly(1L);

        assertThat(store.listDrafts(scope)).isEmpty();
        assertThat(store.listReleases(scope, 10)).hasSize(1);
        assertThat(count("aster_config_publish_event")).isEqualTo(1);
    }

    @Test
    void publishWithoutOpenDraftsDoesNotCreateRevision() {
        ConfigScope scope = new ConfigScope("it", "empty");

        PublishResult result = store.publish(new PublishRequest(scope, "nothing", "tester"));

        assertThat(result.revision()).isZero();
        assertThat(result.changedItems()).isZero();
        assertThat(store.currentRevision(scope)).isZero();
        assertThat(count("aster_config_release")).isZero();
        assertThat(count("aster_config_publish_event")).isZero();
    }

    private DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(env("ASTER_IT_MYSQL_URL",
                "jdbc:mysql://localhost:3306/aster_config_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        dataSource.setUsername(env("ASTER_IT_MYSQL_USERNAME", "root"));
        dataSource.setPassword(env("ASTER_IT_MYSQL_PASSWORD", "123456"));
        return dataSource;
    }

    private String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void clearTables() {
        jdbc.update("delete from aster_config_publish_event");
        jdbc.update("delete from aster_config_release");
        jdbc.update("delete from aster_config_draft");
        jdbc.update("delete from aster_config_document");
        jdbc.update("delete from aster_config_item");
    }

    private int count(String tableName) {
        Integer count = jdbc.queryForObject("select count(*) from " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
