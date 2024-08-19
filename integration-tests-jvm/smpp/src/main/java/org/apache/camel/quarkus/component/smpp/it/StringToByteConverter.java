package org.apache.camel.quarkus.component.smpp.it;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Converter;

@ApplicationScoped
@Converter
public class StringToByteConverter {
    @Converter
    public byte stringToByte(String value) {
        return Byte.parseByte(value);
    }
}
