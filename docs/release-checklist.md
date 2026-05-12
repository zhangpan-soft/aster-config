# Release Checklist

Use this checklist before publishing a public release.

## Required Checks

```bash
mvn clean verify
```

`mvn verify` expects a MySQL database. By default it uses:

```text
jdbc:mysql://localhost:3306/aster_config_it
root / 123456
```

Override with `ASTER_IT_MYSQL_URL`, `ASTER_IT_MYSQL_USERNAME`, and `ASTER_IT_MYSQL_PASSWORD`.

Manual smoke for the runnable app:

```bash
mvn -DskipTests package
java -jar aster-config-app/target/aster-config-app-0.1.0.jar
```

Then verify:

```text
GET http://localhost:8088/aster/embed/config
```

## MySQL Smoke

Create a database and start the integrated profile:

```bash
docker exec mysql-server mysql -uroot -p123456 -e \
  "drop database if exists aster_config_it; create database aster_config_it character set utf8mb4 collate utf8mb4_unicode_ci;"

java -jar aster-config-app/target/aster-config-app-0.1.0.jar \
  --aster.profile=integrated \
  --aster.embed.permit-all=true \
  --spring.datasource.url='jdbc:mysql://localhost:3306/aster_config_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  --spring.datasource.username=root \
  --spring.datasource.password=123456 \
  --spring.sql.init.mode=always \
  --spring.sql.init.schema-locations=classpath:db/aster-config-mysql.sql
```

`aster.embed.permit-all=true` is only for local smoke testing. Production deployments should provide their own `EmbedTokenValidator`.

## Release Artifacts

- `LICENSE`
- `NOTICE`
- `CHANGELOG.md`
- `README.md`
- `SECURITY.md`
- GitHub Actions CI passes
- tag named `v0.1.0`
