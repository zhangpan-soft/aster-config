package io.asterconfig.client.spring;

import io.asterconfig.client.http.HttpAsterConfigClient;
import io.asterconfig.client.http.NettyAsterConfigClient;
import io.asterconfig.client.protocol.AsterClientProperties;
import io.asterconfig.server.protocol.AsterMessage;
import io.asterconfig.server.protocol.ClientConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsterClientRunListener implements SpringApplicationRunListener {

    private static final Logger log = LoggerFactory.getLogger(AsterClientRunListener.class);

    private AsterClientProperties properties;
    private HttpAsterConfigClient client;
    private NettyAsterConfigClient nettyClient;
    private AsterDynamicPropertySource propertySource;
    private volatile long revision;
    private Thread pollThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AsterClientRunListener(SpringApplication application, String[] args) {
    }

    @Override
    public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
        properties = Binder.get(environment)
                .bind("aster.client", AsterClientProperties.class)
                .orElseGet(AsterClientProperties::new);
        if (!properties.isEnabled()) {
            log.info("Aster config client disabled");
            return;
        }
        if (properties.getNamespaces().isEmpty()) {
            String message = "aster.client.namespaces is required when client is enabled";
            if (properties.isFailFast()) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
            return;
        }

        propertySource = new AsterDynamicPropertySource("asterConfig");
        environment.getPropertySources().addFirst(propertySource);
        client = new HttpAsterConfigClient(properties);
        try {
            ClientConfigResponse response = client.loadConfigs();
            revision = response.revision();
            propertySource.replaceProperties(merge(response));
            log.info("Loaded Aster config, env={}, namespaces={}, revision={}",
                    properties.getEnv(), properties.getNamespaces(), revision);
        } catch (Exception e) {
            if (properties.isFailFast()) {
                throw e;
            }
            log.warn("Failed to load Aster config, application will continue", e);
        }
    }

    @Override
    public void started(ConfigurableApplicationContext context, Duration timeTaken) {
        if (client == null || propertySource == null || !running.compareAndSet(false, true)) {
            return;
        }
        pollThread = new Thread(this::pollLoop, "aster-config-poll");
        pollThread.setDaemon(true);
        pollThread.start();
        if (properties.isNettyEnabled()) {
            nettyClient = new NettyAsterConfigClient(properties, this::handleNettyChange);
            try {
                nettyClient.start();
            } catch (Exception e) {
                log.warn("Failed to start Aster Netty client, revision polling will continue as fallback", e);
            }
        }
        context.getBeanFactory().registerSingleton("asterConfigClient", client);
        context.getBeanFactory().registerSingleton("asterDynamicPropertySource", propertySource);
        if (nettyClient != null) {
            context.getBeanFactory().registerSingleton("asterNettyConfigClient", nettyClient);
        }
    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        stopPoll();
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(properties.getPollInterval().toMillis());
                long remoteRevision = client.currentRevision(revision);
                if (remoteRevision <= revision) {
                    continue;
                }
                refreshFromServer("polling", remoteRevision);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Failed to refresh Aster config", e);
            }
        }
    }

    private void handleNettyChange(AsterMessage message) {
        if (message.revision() <= revision) {
            return;
        }
        refreshFromServer("netty", message.revision());
    }

    private synchronized void refreshFromServer(String trigger, long remoteRevision) {
        if (remoteRevision <= revision) {
            return;
        }
        ClientConfigResponse response = client.loadConfigs();
        revision = response.revision();
        propertySource.replaceProperties(merge(response));
        log.info("Refreshed Aster config, trigger={}, revision={}", trigger, revision);
    }

    private Map<String, Object> merge(ClientConfigResponse response) {
        Map<String, Object> result = new TreeMap<>();
        response.configs().values().forEach(namespaceValues -> result.putAll(namespaceValues));
        return result;
    }

    private void stopPoll() {
        running.set(false);
        if (nettyClient != null) {
            nettyClient.close();
        }
        if (pollThread != null) {
            pollThread.interrupt();
        }
    }
}
