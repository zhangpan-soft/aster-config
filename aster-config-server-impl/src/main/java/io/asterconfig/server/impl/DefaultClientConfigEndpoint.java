package io.asterconfig.server.impl;

import io.asterconfig.core.model.ConfigItem;
import io.asterconfig.core.model.ConfigQuery;
import io.asterconfig.core.model.ConfigScope;
import io.asterconfig.core.service.ConfigManagementService;
import io.asterconfig.server.protocol.ClientConfigEndpoint;
import io.asterconfig.server.protocol.ClientConfigQuery;
import io.asterconfig.server.protocol.ClientConfigResponse;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

public class DefaultClientConfigEndpoint implements ClientConfigEndpoint {

    private final ConfigManagementService configService;

    public DefaultClientConfigEndpoint(ConfigManagementService configService) {
        this.configService = configService;
    }

    @Override
    public ClientConfigResponse getConfigs(ClientConfigQuery query) {
        Map<String, TreeMap<String, String>> configs = new TreeMap<>();
        long revision = 0;
        for (String namespace : query.namespaces()) {
            ConfigScope scope = new ConfigScope(query.env(), namespace);
            TreeMap<String, String> namespaceValues = new TreeMap<>();
            for (ConfigItem item : configService.listItems(ConfigQuery.scope(scope))) {
                namespaceValues.put(item.key(), item.value());
            }
            configs.put(namespace, namespaceValues);
            revision = Math.max(revision, configService.listReleases(scope, 1).stream()
                    .mapToLong(release -> release.revision())
                    .findFirst()
                    .orElse(0));
        }
        return new ClientConfigResponse(query.env(), revision, configs, Instant.now());
    }

    @Override
    public long currentRevision(ClientConfigQuery query) {
        long revision = 0;
        for (String namespace : query.namespaces()) {
            ConfigScope scope = new ConfigScope(query.env(), namespace);
            revision = Math.max(revision, configService.listReleases(scope, 1).stream()
                    .mapToLong(release -> release.revision())
                    .findFirst()
                    .orElse(0));
        }
        return revision;
    }
}
