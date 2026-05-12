package io.asterconfig.server.impl;

import io.asterconfig.server.protocol.ClientConfigEndpoint;
import io.asterconfig.server.protocol.ClientConfigQuery;
import io.asterconfig.server.protocol.ClientConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/aster/client/api")
public class ClientConfigController {

    private final ClientConfigEndpoint clientConfigEndpoint;

    public ClientConfigController(ClientConfigEndpoint clientConfigEndpoint) {
        this.clientConfigEndpoint = clientConfigEndpoint;
    }

    @GetMapping("/configs")
    public ClientConfigResponse configs(
            @RequestParam String env,
            @RequestParam String namespaces,
            @RequestParam(defaultValue = "0") long knownRevision
    ) {
        return clientConfigEndpoint.getConfigs(new ClientConfigQuery(env, split(namespaces), knownRevision));
    }

    @GetMapping("/revision")
    public RevisionResponse revision(
            @RequestParam String env,
            @RequestParam String namespaces,
            @RequestParam(defaultValue = "0") long knownRevision
    ) {
        long revision = clientConfigEndpoint.currentRevision(new ClientConfigQuery(env, split(namespaces), knownRevision));
        return new RevisionResponse(revision, revision > knownRevision);
    }

    private List<String> split(String namespaces) {
        return Arrays.stream(namespaces.split(","))
                .map(String::trim)
                .filter(namespace -> !namespace.isBlank())
                .toList();
    }

    public record RevisionResponse(long revision, boolean changed) {
    }
}
