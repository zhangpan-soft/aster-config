package io.asterconfig.codec;

import io.asterconfig.core.model.ConfigValue;
import io.asterconfig.core.model.SourceFormat;
import io.asterconfig.core.model.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCodecRegistryTest {

    private final ConfigCodecRegistry registry = new ConfigCodecRegistry(List.of(
            new JsonConfigCodec(),
            new YamlConfigCodec(),
            new XmlConfigCodec(),
            new PropertiesConfigCodec()
    ));

    @Test
    void flattensYamlToKeyValuePairs() {
        Map<String, ConfigValue> values = registry.flatten(SourceFormat.YAML, """
                spring:
                  datasource:
                    url: jdbc:mysql://localhost:3306/test
                    hikari:
                      maximum-pool-size: 10
                feature:
                  enabled: true
                """);

        assertThat(values).containsEntry("spring.datasource.url", ConfigValue.of("jdbc:mysql://localhost:3306/test"));
        assertThat(values.get("spring.datasource.hikari.maximum-pool-size").valueType()).isEqualTo(ValueType.NUMBER);
        assertThat(values.get("feature.enabled").valueType()).isEqualTo(ValueType.BOOLEAN);
    }

    @Test
    void flattensJsonArraysWithIndexedKeys() {
        Map<String, ConfigValue> values = registry.flatten(SourceFormat.JSON, """
                {"servers":[{"host":"10.0.0.1","port":8080}]}
                """);

        assertThat(values).containsEntry("servers[0].host", ConfigValue.of("10.0.0.1"));
        assertThat(values.get("servers[0].port").value()).isEqualTo("8080");
    }
}
