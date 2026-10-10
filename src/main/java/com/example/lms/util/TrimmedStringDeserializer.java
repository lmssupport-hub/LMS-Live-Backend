package com.example.lms.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Trims leading/trailing whitespace of a JSON string while it is deserialized,
 * i.e. before bean validation (@Email / @Size / @NotBlank) runs.
 */
public class TrimmedStringDeserializer extends StdDeserializer<String> {

    public TrimmedStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        return value == null ? null : value.trim();
    }
} 