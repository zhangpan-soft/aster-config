package io.asterconfig.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.asterconfig.core.model.SourceFormat;

public class JsonConfigCodec extends JacksonTreeConfigCodec {

    public JsonConfigCodec() {
        super(SourceFormat.JSON, new ObjectMapper());
    }
}
