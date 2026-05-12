package io.asterconfig.server.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class JdbcPublishEventPoller implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JdbcPublishEventPoller.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final AsterClusterProperties properties;
    private final ClientSubscriptionRegistry subscriptionRegistry;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant watermark = Instant.EPOCH;
    private Thread pollThread;

    public JdbcPublishEventPoller(
            DataSource dataSource,
            AsterClusterProperties properties,
            ClientSubscriptionRegistry subscriptionRegistry
    ) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.properties = properties;
        this.subscriptionRegistry = subscriptionRegistry;
    }

    @Override
    public void start() {
        if (!properties.isEnabled()
                || !properties.isPublishEventPollerEnabled()
                || !running.compareAndSet(false, true)) {
            return;
        }
        watermark = Instant.now().minusSeconds(30);
        pollThread = new Thread(this::pollLoop, "aster-publish-event-poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(properties.getPublishEventPollInterval().toMillis());
                pollOnce();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Failed to poll Aster publish events", e);
            }
        }
    }

    private void pollOnce() {
        List<PublishEvent> events = jdbc.query("""
                select env, namespace, revision, created_at
                from aster_config_publish_event
                where created_at > :watermark
                order by created_at asc
                limit 200
                """, new MapSqlParameterSource("watermark", Timestamp.from(watermark)), (rs, rowNum) ->
                new PublishEvent(
                        rs.getString("env"),
                        rs.getString("namespace"),
                        rs.getLong("revision"),
                        rs.getTimestamp("created_at").toInstant()
                ));
        for (PublishEvent event : events) {
            subscriptionRegistry.publish(event.env(), event.namespace(), event.revision());
            if (event.createdAt().isAfter(watermark)) {
                watermark = event.createdAt();
            }
        }
    }

    private record PublishEvent(String env, String namespace, long revision, Instant createdAt) {
    }
}
