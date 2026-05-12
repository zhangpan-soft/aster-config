package io.asterconfig.codec;

import io.asterconfig.core.model.ConfigValue;
import io.asterconfig.core.model.SourceFormat;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class PropertiesConfigCodec implements ConfigCodec {

    @Override
    public SourceFormat sourceFormat() {
        return SourceFormat.PROPERTIES;
    }

    @Override
    public Map<String, ConfigValue> flatten(String source) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(source == null ? "" : source));
        } catch (IOException e) {
            throw new ConfigCodecException("Failed to parse properties source", e);
        }
        Map<String, ConfigValue> result = new TreeMap<>();
        properties.stringPropertyNames().forEach(key -> result.put(key, ConfigValue.of(properties.getProperty(key))));
        return result;
    }

    @Override
    public String render(Map<String, ConfigValue> values) {
        Properties properties = new Properties();
        values.forEach((key, value) -> properties.setProperty(key, value.value() == null ? "" : value.value()));
        StringWriter writer = new StringWriter();
        try {
            properties.store(writer, null);
        } catch (IOException e) {
            throw new ConfigCodecException("Failed to render properties source", e);
        }
        return writer.toString();
    }
}
