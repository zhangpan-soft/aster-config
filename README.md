# Aster Config

Aster Config is an embeddable configuration management engine for business systems.

It is not positioned as another standalone Nacos console. The goal is to let an existing business admin system manage runtime configuration with the same users, permissions, audit trail, approval flow, and operational context that the business already uses.

## Status

Current release: `0.1.0`

This first public version is ready for preview and early integration. The core model, local store, JDBC store, embedded canvas, client API, and Netty notification path are available. Production users should provide their own authentication and migration process before rollout.

## Features

- Embedded admin canvas for JSON, YAML, XML, and properties.
- Normalized key/value storage with `env + namespace` identity.
- Local file profile for demos and small tools.
- JDBC integrated profile for business-system production integration.
- Client HTTP API and Spring Boot client starter.
- Netty revision notification with HTTP polling fallback.
- Cluster peer notification without requiring Redis.
- JDBC publish-event compensation for missed cluster notifications.

## Profiles

| Profile | Purpose | Store | Notes |
| --- | --- | --- | --- |
| `local` | development, demo, small local tools | local files | no external dependency |
| `integrated` | production integration with business admin systems | JDBC | database is the source of truth |

Redis is not required. It can be added later as an accelerator, not as the authoritative store.

## Modules

| Module | Responsibility |
| --- | --- |
| `aster-config-core` | domain model, store SPI, management service |
| `aster-config-codec` | JSON/YAML/XML/properties parsing and key/value flattening |
| `aster-config-server` | server-side protocol and client consumption contract |
| `aster-config-store-file` | local file store for the `local` profile |
| `aster-config-store-jdbc` | JDBC store for the `integrated` profile |
| `aster-config-admin-api` | REST API and embeddable config canvas |
| `aster-config-server-impl` | Spring server implementation, admin API, store wiring, client endpoints, Netty push |
| `aster-config-client` | client SDK core implementation |
| `aster-config-client-spring-boot-starter` | Spring Boot startup loading and runtime polling |
| `aster-config-spring-boot-starter` | server-side auto-configuration internals |
| `aster-config-app` | runnable local app |

## Quickstart

Build and run the local profile:

```bash
mvn clean package
java -jar aster-config-app/target/aster-config-app-0.1.0.jar
```

Open:

```text
http://localhost:8088/aster/embed/config
```

The embedded page parses source documents into normalized key/value drafts, then publishes them into the selected store.

## MySQL Integrated Mode

Create a database:

```bash
docker exec mysql-server mysql -uroot -p123456 -e \
  "create database if not exists aster_config character set utf8mb4 collate utf8mb4_unicode_ci;"
```

Run:

```bash
java -jar aster-config-app/target/aster-config-app-0.1.0.jar \
  --aster.profile=integrated \
  --aster.embed.permit-all=true \
  --spring.datasource.url='jdbc:mysql://localhost:3306/aster_config?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  --spring.datasource.username=root \
  --spring.datasource.password=123456 \
  --spring.sql.init.mode=always \
  --spring.sql.init.schema-locations=classpath:db/aster-config-mysql.sql
```

`aster.embed.permit-all=true` is only for local smoke testing. In production, provide an `EmbedTokenValidator` bean and keep permit-all disabled.

## Business Service Usage

Business services should use the client starter, not the admin API.

```xml
<dependency>
  <groupId>io.asterconfig</groupId>
  <artifactId>aster-config-client-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

```yaml
aster:
  client:
    server-addr: http://localhost:8088
    netty-host: localhost
    netty-port: 9088
    env: dev
    namespaces:
      - common
      - redis
      - casino-user
    fail-fast: true
    poll-interval: 60s
```

The starter loads config before Spring beans are initialized, then inserts the remote key/value map into the highest-priority Spring `Environment` property source.

Application code keeps using normal Spring configuration APIs:

```java
@Value("${redis.host}")
private String redisHost;
```

or:

```java
@ConfigurationProperties(prefix = "redis")
public class RedisProperties {
    private String host;
    private int port;
}
```

Client-facing endpoints are separated from admin endpoints:

```text
GET /aster/client/api/configs?env=dev&namespaces=common,redis
GET /aster/client/api/revision?env=dev&namespaces=common,redis&knownRevision=1
```

Realtime change notification uses Netty. The server pushes only revision and changed namespaces:

```json
{"type":"CONFIG_CHANGE_NOTIFY","env":"dev","namespaces":["common"],"revision":2}
```

The client then reloads config through `/aster/client/api/configs`. HTTP revision polling remains enabled as a fallback if the Netty notification is missed.

## Cluster Mode

Cluster mode still uses JDBC as the source of truth. Netty is only the fast node-to-node notification path:

```yaml
aster:
  profile: integrated
  cluster:
    enabled: true
    node-id: node-1
    peers:
      - node-id: node-2
        host: 10.0.0.12
        port: 9088
      - node-id: node-3
        host: 10.0.0.13
        port: 9088
```

When node 1 publishes `dev/common`, it pushes local clients and sends a small Netty node message to peers. Peer nodes push their own connected clients with the new revision, and clients reload only when the revision is newer. With JDBC enabled, nodes also poll `aster_config_publish_event` as a compensation path.

## Production Embedding

The default embedded-token policy is:

- `local`: permit all, for development
- `integrated`: deny all, unless `aster.embed.permit-all=true` or a custom `EmbedTokenValidator` bean is provided

Production systems should provide:

```java
@Bean
EmbedTokenValidator embedTokenValidator() {
    return (token, scope, action) -> {
        // Validate host-system session, permission, env/namespace scope, and action.
        return true;
    };
}
```

You should also provide a `UserProvider` bean to connect release records to your business admin user identity.

## Development

Fast test suite:

```bash
mvn clean test
```

Full verification, including MySQL integration tests:

```bash
mvn clean verify
```

The integration test defaults to `jdbc:mysql://localhost:3306/aster_config_it` with user `root` and password `123456`. Override with `ASTER_IT_MYSQL_URL`, `ASTER_IT_MYSQL_USERNAME`, and `ASTER_IT_MYSQL_PASSWORD`.

See [docs/architecture.md](docs/architecture.md) and [docs/release-checklist.md](docs/release-checklist.md).

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).
