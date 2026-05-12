package io.asterconfig.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.asterconfig.core.model.ConfigValue;
import io.asterconfig.core.model.SourceFormat;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

abstract class JacksonTreeConfigCodec implements ConfigCodec {

    private final SourceFormat sourceFormat;
    private final ObjectMapper objectMapper;

    protected JacksonTreeConfigCodec(SourceFormat sourceFormat, ObjectMapper objectMapper) {
        this.sourceFormat = sourceFormat;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceFormat sourceFormat() {
        return sourceFormat;
    }

    @Override
    public Map<String, ConfigValue> flatten(String source) {
        try {
            JsonNode root = objectMapper.readTree(source == null ? "" : source);
            Map<String, ConfigValue> result = new TreeMap<>();
            flattenNode("", root, result);
            return result;
        } catch (Exception e) {
            throw new ConfigCodecException("Failed to parse " + sourceFormat + " source", e);
        }
    }

    @Override
    public String render(Map<String, ConfigValue> values) {
        Map<String, Object> flatValues = new TreeMap<>();
        values.forEach((key, value) -> flatValues.put(key, typedValue(value)));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(flatValues);
        } catch (JsonProcessingException e) {
            throw new ConfigCodecException("Failed to render " + sourceFormat + " source", e);
        }
    }

    private void flattenNode(String path, JsonNode node, Map<String, ConfigValue> result) {
        if (node == null || node.isNull()) {
            if (!path.isBlank()) {
                result.put(path, ConfigValue.of(null));
            }
            return;
        }
        if (node.isValueNode()) {
            if (!path.isBlank()) {
                result.put(path, ConfigValue.of(node.asText()));
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flattenNode(path + "[" + i + "]", node.get(i), result);
            }
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String childPath = path.isBlank() ? field.getKey() : path + "." + field.getKey();
            flattenNode(childPath, field.getValue(), result);
        }
    }

    private Object typedValue(ConfigValue value) {
        if (value == null || value.valueType() == null) {
            return null;
        }
        return switch (value.valueType()) {
            case NULL -> null;
            case BOOLEAN -> Boolean.parseBoolean(value.value());
            case NUMBER -> numberValue(value.value());
            case STRING -> value.value();
        };
    }

    private Number numberValue(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        if (value.contains(".")) {
            return Double.parseDouble(value);
        }
        return Long.parseLong(value);
    }
}
