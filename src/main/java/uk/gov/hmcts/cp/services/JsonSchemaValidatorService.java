package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JsonSchemaValidatorService {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final EnumMap<PublicationSchema, JsonSchema> schemaCache = new EnumMap<>(PublicationSchema.class);

    @PostConstruct
    void preloadSchemas() {
        for (PublicationSchema schema : PublicationSchema.values()) {
            schemaCache.put(schema, loadSchema(schema));
        }
    }

    public void validate(CourtListDocument document, PublicationSchema schema) {
        if (document == null) {
            throw new SchemaValidationException("Document cannot be null");
        }
        try {
            validateJsonNode(OBJECT_MAPPER.valueToTree(document), schema);
        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaValidationException("Failed to convert document to JSON: " + e.getMessage(), e);
        }
    }

    public void validate(String json, PublicationSchema schema) {
        if (json == null || json.isBlank()) {
            throw new SchemaValidationException("JSON cannot be null or blank");
        }
        try {
            validateJsonNode(OBJECT_MAPPER.readTree(json), schema);
        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaValidationException("JSON schema validation failed: " + e.getMessage(), e);
        }
    }

    private void validateJsonNode(JsonNode jsonNode, PublicationSchema schema) {
        log.debug("Validating against JSON schema: {}", schema.path());
        Set<ValidationMessage> errors = schemaCache.computeIfAbsent(schema, this::loadSchema).validate(jsonNode);
        if (!errors.isEmpty()) {
            log.error("JSON schema validation failed");
            String messages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("\n"));
            throw new SchemaValidationException("JSON schema validation failed:\n" + messages);
        }
        log.debug("JSON schema validation passed");
    }

    private JsonSchema loadSchema(PublicationSchema publicationSchema) {
        ClassPathResource resource = new ClassPathResource(publicationSchema.path());
        if (!resource.exists()) {
            throw new IllegalStateException("Schema file not found: " + publicationSchema.path());
        }
        try (InputStream in = resource.getInputStream()) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = factory.getSchema(in);
            log.info("JSON schema loaded and cached from {}", publicationSchema.path());
            return schema;
        } catch (IOException e) {
            throw new SchemaValidationException(
                    "Failed to load JSON schema from " + publicationSchema.path() + ": " + e.getMessage(), e);
        }
    }
}
