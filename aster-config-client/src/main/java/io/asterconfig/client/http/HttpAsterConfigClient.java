package io.asterconfig.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.asterconfig.client.protocol.AsterClientProperties;
import io.asterconfig.client.protocol.AsterConfigClient;
import io.asterconfig.server.protocol.ClientConfigResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

public class HttpAsterConfigClient implements AsterConfigClient {

    private final AsterClientProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpAsterConfigClient(AsterClientProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public ClientConfigResponse loadConfigs() {
        HttpRequest request = HttpRequest.newBuilder(configsUri(0))
                .timeout(properties.getRequestTimeout())
                .GET()
                .build();
        return send(request, ClientConfigResponse.class);
    }

    @Override
    public long currentRevision(long knownRevision) {
        HttpRequest request = HttpRequest.newBuilder(revisionUri(knownRevision))
                .timeout(properties.getRequestTimeout())
                .GET()
                .build();
        RevisionResponse response = send(request, RevisionResponse.class);
        return response.revision();
    }

    private URI configsUri(long knownRevision) {
        return URI.create(properties.getServerAddr() + "/aster/client/api/configs?" + query(knownRevision));
    }

    private URI revisionUri(long knownRevision) {
        return URI.create(properties.getServerAddr() + "/aster/client/api/revision?" + query(knownRevision));
    }

    private String query(long knownRevision) {
        return new StringJoiner("&")
                .add("env=" + encode(properties.getEnv()))
                .add("namespaces=" + encode(String.join(",", properties.getNamespaces())))
                .add("knownRevision=" + knownRevision)
                .toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Aster config request failed, status=" + response.statusCode()
                        + ", body=" + response.body());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new IllegalStateException("Aster config request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Aster config request interrupted", e);
        }
    }

    public record RevisionResponse(long revision, boolean changed) {
    }
}
