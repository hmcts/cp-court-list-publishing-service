package uk.gov.hmcts.cp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Provides a shared ObjectMapper instance configured with NON_NULL serialization,
 * Java 8 date/time support, and ignoring unknown JSON properties during deserialization.
 */
public final class ObjectMapperConfig {

    private ObjectMapperConfig() {
        // utility class
    }

    @SuppressWarnings("deprecation")
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
