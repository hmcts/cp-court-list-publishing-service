package uk.gov.hmcts.cp.pact.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Objects;

public class JsonFileToObject {

    private static final Logger LOG = LoggerFactory.getLogger(JsonFileToObject.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static <T> T readJsonFromResources(final String fileName, final Class<T> clazz) throws Exception {
        final File file;
        try{
            //file = new File(Objects.requireNonNull(JsonFileToObject.class.getClassLoader().getResource(fileName)).toURI());
            file = new File(Objects.requireNonNull(
                    Thread.currentThread().getContextClassLoader().getResource(fileName)
            ).toURI());
        } catch (final Exception exception) {
            LOG.atError().log("Error loading file: {}", fileName, exception);
            throw exception;
        }
        return OBJECT_MAPPER.readValue(file, clazz);
    }
}