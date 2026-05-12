package io.asterconfig.codec;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.asterconfig.core.model.SourceFormat;

public class XmlConfigCodec extends JacksonTreeConfigCodec {

    public XmlConfigCodec() {
        super(SourceFormat.XML, new XmlMapper());
    }
}
