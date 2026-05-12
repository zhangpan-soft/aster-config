# Contributing

Thanks for helping improve Aster Config.

## Development

Requirements:

- JDK 17
- Maven 3.9+
- MySQL, when running integration tests

Common commands:

```bash
mvn clean test
mvn verify
```

`mvn verify` runs integration tests against MySQL. By default it uses `jdbc:mysql://localhost:3306/aster_config_it`, user `root`, and password `123456`. Override with `ASTER_IT_MYSQL_URL`, `ASTER_IT_MYSQL_USERNAME`, and `ASTER_IT_MYSQL_PASSWORD`.

## Pull Requests

- Keep changes focused.
- Add or update tests for behavior changes.
- Update documentation when configuration, API, or user-facing behavior changes.
- Run `mvn verify` before opening a release-oriented pull request.

## License

By contributing, you agree that your contributions are licensed under the Apache License, Version 2.0.
