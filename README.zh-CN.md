# Aster Config

[English](README.md)

Aster Config 是一个面向业务系统的可嵌入配置管理引擎。

它不是为了再做一个独立配置控制台。它解决的问题是：很多业务配置本来就应该由现有业务后台统一管理，但传统配置中心往往会变成另一套管理系统，无法自然接入原有的用户、权限、审计、审批和运营流程。

## 为什么会有这个项目

很多团队已经有成熟的业务管理后台。这个后台里有用户体系、角色权限、操作审计、审批流程、租户或商户上下文，也有运营人员日常使用的业务页面。业务运行时配置也天然属于这里，例如：

- 功能开关
- 支付规则
- 风控参数
- 活动规则
- 路由规则
- 第三方渠道配置
- 运营可调整的业务参数

如果这些配置被放进独立配置中心，常见问题是：

- 运营或业务人员需要学习另一套控制台
- 权限模型和现有后台割裂
- 配置变更无法自然进入原有审批和审计流程
- 业务页面无法把配置编辑作为当前流程的一部分嵌入
- 为了把独立配置中心接入现有后台，反而要做大量适配工作

独立配置中心适合需要单独配置平台的团队。Aster Config 的定位不同：当配置本身属于业务管理域，并且你希望它嵌入自己的管理后台、复用自己的数据库、接入自己的鉴权、权限、审计和运营模型时，Aster Config 提供的是这部分能力。

一句话：

- 需要独立配置中心：使用独立配置平台。
- 需要嵌入现有业务后台的配置管理引擎：使用 Aster Config。

## 当前状态

当前版本：`0.1.0`

第一个公开版本适合预览和早期集成。核心模型、本地文件存储、JDBC 存储、嵌入式画布、客户端 API、Netty 通知链路都已经可用。生产环境接入前，应配置自己的鉴权和数据库迁移流程。

## 功能

- 支持 JSON、YAML、XML、properties 的嵌入式配置画布。
- 使用 `env + namespace` 作为配置身份。
- 最终配置以 key/value 形式存储和下发。
- `local` 模式使用本地文件，适合开发、演示、小工具。
- `integrated` 模式使用 JDBC，适合接入业务系统生产环境。
- 提供客户端 HTTP API 和 Spring Boot client starter。
- Netty 推送 revision 通知，HTTP 拉取真实配置内容。
- HTTP revision 轮询兜底，避免错过通知后无法恢复。
- 支持集群节点间 Netty 通知，不强依赖 Redis。
- JDBC `publish_event` 作为集群通知补偿路径。

## 运行模式

| 模式 | 目的 | 存储 | 说明 |
| --- | --- | --- | --- |
| `local` | 开发、演示、小型本地工具 | 本地文件 | 无外部依赖 |
| `integrated` | 和业务后台生产集成 | JDBC | 数据库是权威数据源 |

Redis 不是必需依赖。后续可以作为加速器加入，但不会作为权威存储。

## 模块

| 模块 | 职责 |
| --- | --- |
| `aster-config-core` | 领域模型、存储 SPI、管理服务 |
| `aster-config-codec` | JSON/YAML/XML/properties 解析与 key/value 扁平化 |
| `aster-config-server` | 服务端协议和客户端消费契约 |
| `aster-config-store-file` | `local` 模式本地文件存储 |
| `aster-config-store-jdbc` | `integrated` 模式 JDBC 存储 |
| `aster-config-admin-api` | 管理 REST API 和嵌入式配置画布 |
| `aster-config-server-impl` | Spring 服务端实现、管理 API、客户端端点、Netty 推送 |
| `aster-config-client` | 客户端 SDK 核心实现 |
| `aster-config-client-spring-boot-starter` | Spring Boot 启动加载和运行时刷新 |
| `aster-config-server-spring-boot-starter` | 服务端 Spring Boot 自动配置 |
| `aster-config-app` | 可运行示例应用 |

## 快速开始

构建并运行本地模式：

```bash
mvn clean package
java -jar aster-config-app/target/aster-config-app-0.1.0.jar
```

打开：

```text
http://localhost:8088/aster/embed/config
```

页面会把源文档解析为 key/value 草稿，发布后写入当前存储。

## MySQL 集成模式

创建数据库：

```bash
docker exec mysql-server mysql -uroot -p123456 -e \
  "create database if not exists aster_config character set utf8mb4 collate utf8mb4_unicode_ci;"
```

启动：

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

`aster.embed.permit-all=true` 只适合本地 smoke 测试。生产环境应提供自己的 `EmbedTokenValidator`，并保持 permit-all 关闭。

## 业务服务如何接入

业务服务应接入 client starter，而不是调用管理 API。

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

starter 会在 Spring Bean 初始化前加载配置，并把远程 key/value 放进最高优先级的 Spring `Environment` property source。

业务代码继续使用普通 Spring 配置方式：

```java
@Value("${redis.host}")
private String redisHost;
```

或者：

```java
@ConfigurationProperties(prefix = "redis")
public class RedisProperties {
    private String host;
    private int port;
}
```

客户端端点和管理端点是分离的：

```text
GET /aster/client/api/configs?env=dev&namespaces=common,redis
GET /aster/client/api/revision?env=dev&namespaces=common,redis&knownRevision=1
```

实时通知使用 Netty。服务端只推送 revision 和变更 namespace：

```json
{"type":"CONFIG_CHANGE_NOTIFY","env":"dev","namespaces":["common"],"revision":2}
```

客户端收到通知后，再通过 `/aster/client/api/configs` 拉取真实配置内容。HTTP revision 轮询会持续存在，作为错过 Netty 通知后的兜底。

## 集群模式

集群模式仍然以 JDBC 作为权威数据源。Netty 只是节点间快速通知路径：

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

节点 1 发布 `dev/common` 后，会先推送本节点客户端，再通过 Netty 通知 peer 节点。peer 节点再推送自己连接的客户端。客户端只有在 revision 更新时才会重新拉取配置。JDBC 模式下，节点还会轮询 `aster_config_publish_event` 作为补偿路径。

## 生产嵌入

默认嵌入式 token 策略：

- `local`：默认允许，便于开发
- `integrated`：默认拒绝，除非显式设置 `aster.embed.permit-all=true` 或提供自定义 `EmbedTokenValidator`

生产系统应提供：

```java
@Bean
EmbedTokenValidator embedTokenValidator() {
    return (token, scope, action) -> {
        // 校验业务后台 session、权限、env/namespace 范围和操作类型。
        return true;
    };
}
```

同时建议提供 `UserProvider`，让发布记录关联到业务后台的真实用户。

## 开发

快速测试：

```bash
mvn clean test
```

完整验证，包括 MySQL 集成测试：

```bash
mvn clean verify
```

集成测试默认连接 `jdbc:mysql://localhost:3306/aster_config_it`，用户名 `root`，密码 `123456`。可通过 `ASTER_IT_MYSQL_URL`、`ASTER_IT_MYSQL_USERNAME`、`ASTER_IT_MYSQL_PASSWORD` 覆盖。

更多设计见 [docs/architecture.md](docs/architecture.md) 和 [docs/release-checklist.md](docs/release-checklist.md)。

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).
