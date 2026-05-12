package io.asterconfig.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.asterconfig.core.model.SourceFormat;

public class YamlConfigCodec extends JacksonTreeConfigCodec {

    public YamlConfigCodec() {
        super(SourceFormat.YAML, new ObjectMapper(new YAMLFactory()));
    }
}
