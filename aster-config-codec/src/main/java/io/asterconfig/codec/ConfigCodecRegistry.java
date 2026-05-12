package io.asterconfig.codec;

import io.asterconfig.core.model.ConfigValue;
import io.asterconfig.core.model.SourceFormat;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public class ConfigCodecRegistry {

    private final Map<SourceFormat, ConfigCodec> codecs = new EnumMap<>(SourceFormat.class);

    public ConfigCodecRegistry(Collection<ConfigCodec> codecs) {
        codecs.forEach(codec -> this.codecs.put(codec.sourceFormat(), codec));
    }

    public Map<String, ConfigValue> flatten(SourceFormat sourceFormat, String source) {
        return codec(sourceFormat).flatten(source);
    }

    public String render(SourceFormat sourceFormat, Map<String, ConfigValue> values) {
        return codec(sourceFormat).render(values);
    }

    public ConfigCodec codec(SourceFormat sourceFormat) {
        ConfigCodec codec = codecs.get(sourceFormat);
        if (codec == null) {
            throw new IllegalArgumentException("Unsupported source format: " + sourceFormat);
        }
        return codec;
    }
}
