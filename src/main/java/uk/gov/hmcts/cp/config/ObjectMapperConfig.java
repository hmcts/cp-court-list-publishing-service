package uk.gov.hmcts.cp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Provides a shared ObjectMapper instance configured with NON_NULL serialization
 * and Java 8 date/time support.
 */
public final class ObjectMapperConfig {

    private ObjectMapperConfig() {
        // utility class
    }

    @SuppressWarnings("deprecation")
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
