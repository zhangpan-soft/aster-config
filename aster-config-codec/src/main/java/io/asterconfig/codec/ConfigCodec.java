package io.asterconfig.codec;

import io.asterconfig.core.model.ConfigValue;
import io.asterconfig.core.model.SourceFormat;

import java.util.Map;

public interface ConfigCodec {

    SourceFormat sourceFormat();

    Map<String, ConfigValue> flatten(String source);

    String render(Map<String, ConfigValue> values);
}
