package io.asterconfig.server.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.asterconfig.core.service.ConfigManagementService;
import io.asterconfig.core.spi.ConfigPublishListener;
import io.asterconfig.server.protocol.ClientConfigEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
@EnableConfigurationProperties({AsterNettyServerProperties.class, AsterClusterProperties.class})
public class AsterServerImplAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClientConfigEndpoint clientConfigEndpoint(ConfigManagementService configManagementService) {
        return new DefaultClientConfigEndpoint(configManagementService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientConfigController clientConfigController(ClientConfigEndpoint clientConfigEndpoint) {
        return new ClientConfigController(clientConfigEndpoint);
    }

    @Bean
    @ConditionalOnMissingBean(name = "asterNettyObjectMapper")
    public ObjectMapper asterNettyObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientSubscriptionRegistry clientSubscriptionRegistry(ObjectMapper asterNettyObjectMapper) {
        return new ClientSubscriptionRegistry(asterNettyObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsterNettyServerHandler asterNettyServerHandler(
            ObjectMapper asterNettyObjectMapper,
            ClientSubscriptionRegistry clientSubscriptionRegistry,
            ClientConfigEndpoint clientConfigEndpoint,
            AsterClusterProperties clusterProperties
    ) {
        return new AsterNettyServerHandler(asterNettyObjectMapper, clientSubscriptionRegistry, clientConfigEndpoint,
                clusterProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsterNettyServer asterNettyServer(
            AsterNettyServerProperties properties,
            AsterNettyServerHandler handler
    ) {
        return new AsterNettyServer(properties, handler);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClusterPeerPublisher clusterPeerPublisher(
            AsterClusterProperties properties,
            ObjectMapper asterNettyObjectMapper
    ) {
        if (!properties.isEnabled()) {
            return new NoopClusterPeerPublisher();
        }
        return new ClusterPeerNotifier(properties, asterNettyObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "nettyConfigPublishListener")
    public ConfigPublishListener nettyConfigPublishListener(
            ClientSubscriptionRegistry clientSubscriptionRegistry,
            ClusterPeerPublisher clusterPeerPublisher
    ) {
        return new NettyConfigPublishListener(clientSubscriptionRegistry, clusterPeerPublisher);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public JdbcPublishEventPoller jdbcPublishEventPoller(
            DataSource dataSource,
            AsterClusterProperties clusterProperties,
            ClientSubscriptionRegistry clientSubscriptionRegistry
    ) {
        return new JdbcPublishEventPoller(dataSource, clusterProperties, clientSubscriptionRegistry);
    }
}
