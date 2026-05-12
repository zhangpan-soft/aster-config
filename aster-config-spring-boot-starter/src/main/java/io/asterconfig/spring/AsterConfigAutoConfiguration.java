package io.asterconfig.spring;

import io.asterconfig.admin.api.AsterAdminController;
import io.asterconfig.admin.api.AsterEmbedPageController;
import io.asterconfig.codec.ConfigCodec;
import io.asterconfig.codec.ConfigCodecRegistry;
import io.asterconfig.codec.JsonConfigCodec;
import io.asterconfig.codec.PropertiesConfigCodec;
import io.asterconfig.codec.XmlConfigCodec;
import io.asterconfig.codec.YamlConfigCodec;
import io.asterconfig.core.service.ConfigManagementService;
import io.asterconfig.core.spi.ConfigStore;
import io.asterconfig.core.spi.ConfigPublishListener;
import io.asterconfig.core.spi.EmbedTokenValidator;
import io.asterconfig.core.spi.UserProvider;
import io.asterconfig.store.file.FileConfigStore;
import io.asterconfig.store.jdbc.JdbcConfigStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration
@AutoConfigureAfter({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        TransactionAutoConfiguration.class
})
@EnableConfigurationProperties(AsterConfigProperties.class)
public class AsterConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JsonConfigCodec jsonConfigCodec() {
        return new JsonConfigCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public YamlConfigCodec yamlConfigCodec() {
        return new YamlConfigCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public XmlConfigCodec xmlConfigCodec() {
        return new XmlConfigCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public PropertiesConfigCodec propertiesConfigCodec() {
        return new PropertiesConfigCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigCodecRegistry configCodecRegistry(List<ConfigCodec> codecs) {
        return new ConfigCodecRegistry(codecs);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "aster", name = "profile", havingValue = "local", matchIfMissing = true)
    public ConfigStore fileConfigStore(AsterConfigProperties properties) {
        return new FileConfigStore(properties.getDataDir());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnBean({DataSource.class, PlatformTransactionManager.class})
    @ConditionalOnProperty(prefix = "aster", name = "profile", havingValue = "integrated")
    public ConfigStore jdbcConfigStore(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new JdbcConfigStore(dataSource, new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigManagementService configManagementService(ConfigStore configStore, List<ConfigPublishListener> publishListeners) {
        return new ConfigManagementService(configStore, publishListeners);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserProvider asterUserProvider() {
        return UserProvider.system();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbedTokenValidator embedTokenValidator(AsterConfigProperties properties) {
        if (properties.isEmbedPermitAll()) {
            return EmbedTokenValidator.permitAll();
        }
        return EmbedTokenValidator.denyAll();
    }

    @Bean
    @ConditionalOnMissingBean
    public AsterAdminController asterAdminController(
            ConfigManagementService configManagementService,
            ConfigCodecRegistry configCodecRegistry,
            UserProvider userProvider,
            EmbedTokenValidator embedTokenValidator
    ) {
        return new AsterAdminController(configManagementService, configCodecRegistry, userProvider, embedTokenValidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsterEmbedPageController asterEmbedPageController() {
        return new AsterEmbedPageController();
    }
}
