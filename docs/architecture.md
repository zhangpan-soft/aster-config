# Architecture

## Positioning

Aster Config is store-first and integration-first.

The primary production profile is `integrated`: the business database is the source of truth, and Aster provides configuration domain logic, format parsing, drafts, releases, history, and embeddable management APIs.

The project exists because many runtime settings are business configuration, not only service configuration. Teams often already have a business admin system with users, permissions, audit logs, approval flows, tenant or merchant context, and operator-facing workflows. Moving those settings into a standalone configuration console creates a second management island and makes it hard to reuse the host system's permission and audit model.

Aster Config is not intended to replace standalone configuration centers. It targets the case where the configuration experience should be part of an existing business platform: embedded pages, existing identity, existing approval flow, existing database, and business-domain APIs.

Use a standalone configuration center when you need a separate configuration platform. Use Aster Config when configuration management must be embedded into your own business admin system.

## Runtime Profiles

### local

`local` is for development and demos.

```yaml
aster:
  profile: local
  data-dir: ./data/aster-config
```

It uses the file store and persists:

```text
items.json
drafts.json
documents.json
releases.json
```

### integrated

`integrated` is the production profile.

```yaml
aster:
  profile: integrated
```

The application provides its normal `DataSource` and `PlatformTransactionManager`. Aster stores normalized config items, drafts, source documents, and release records in JDBC tables.

## Write Flow

```text
source document
  -> codec parses JSON/YAML/XML/properties
  -> flattened key/value map
  -> generated drafts
  -> publish
  -> config items + release revision
  -> local cache/push layer
```

## Store Contract

`ConfigStore` is the only persistence SPI in the first version. It owns:

- item listing
- draft saving
- source document persistence
- release publication
- current revision lookup

This keeps the public product surface small. Users pick a profile instead of combining store/cache/notification options.

## Module Boundary

Aster follows the same main shape as the current `casino-config` project:

```text
aster-config-server
  protocol DTOs and server contract

aster-config-server-impl
  Spring implementation, store wiring, admin API, embeddable UI, client REST endpoints, Netty push server

aster-config-server-spring-boot-starter
  server-side Spring Boot auto-configuration

aster-config-client
  HTTP config loading and Netty change-notification client

aster-config-client-spring-boot-starter
  Spring Boot startup integration and runtime polling
```

`core`, `codec`, and `store-*` are lower-level support modules. They are not the primary mental model for business users.

## Change Notification

Realtime notification is split into two paths:

```text
Netty: notify env + namespaces + revision
HTTP: reload actual key/value config
revision polling: fallback
```

Publishing a namespace calls `ConfigPublishListener`. The server implementation provides `NettyConfigPublishListener`, which sends:

```json
{"type":"CONFIG_CHANGE_NOTIFY","env":"dev","namespaces":["common"],"revision":2}
```

The client receives the message, checks the revision, then reloads through:

```text
GET /aster/client/api/configs?env=dev&namespaces=common,redis
```

This keeps Netty messages small and makes missed notifications recoverable through revision polling.

## Cluster Coordination

Cluster mode does not make Netty the source of truth. The store still owns revision and config content.

```text
node A publishes dev/common
  -> JDBC transaction writes items, release, publish_event
  -> node A pushes its local clients
  -> node A sends NODE_PUBLISH_NOTIFY to peer nodes through Netty

node B receives NODE_PUBLISH_NOTIFY
  -> publish CONFIG_CHANGE_NOTIFY to node B's local clients
  -> clients reload through HTTP configs endpoint

if node B misses Netty
  -> JDBC publish_event poller sees the revision
  -> node B pushes local clients
```

The identity model is intentionally `env + namespace`. There is no `app` dimension in the core model, so `app1/common` and `app2/common` are the same namespace if they point to the same Aster server and environment. If a team needs isolation, it should create a different namespace such as `app1-common` or use a different environment.

Cluster configuration:

```yaml
aster:
  cluster:
    enabled: true
    node-id: node-1
    reconnect-delay: 2s
    heartbeat-interval: 15s
    publish-event-poller-enabled: true
    publish-event-poll-interval: 5s
    peers:
      - node-id: node-2
        host: 10.0.0.12
        port: 9088
```

## Embed Integration

The embeddable canvas is available at:

```text
/aster/embed/config
```

Business systems can pass a signed token:

```text
/aster/embed/config?token=...
```

The default `EmbedTokenValidator` permits all requests only in the `local` profile. In the `integrated` profile, the default validator denies write actions unless `aster.embed.permit-all=true` is explicitly set or the host application provides its own bean.

Production systems should provide their own bean and validate the token against their admin session, env/namespace scope, and action:

```java
@Bean
EmbedTokenValidator embedTokenValidator() {
    return (token, scope, action) -> {
        // Validate business user, permission, and scope.
        return true;
    };
}
```

## JDBC Schema

The first schema is defined in:

```text
aster-config-store-jdbc/src/main/java/io/asterconfig/store/jdbc/JdbcSchema.java
```

Tables:

- `aster_config_item`
- `aster_config_draft`
- `aster_config_document`
- `aster_config_release`
- `aster_config_publish_event`

The next step is to add database migration files and per-database SQL variants.
