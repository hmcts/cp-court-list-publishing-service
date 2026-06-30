package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.config.ObjectMapperConfig;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;

@Service
@Slf4j
public class JsonSchemaValidatorService {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperConfig.getObjectMapper();

    private final EnumMap<PublicationSchema, Schema> schemaCache = new EnumMap<>(PublicationSchema.class);

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
        validateJsonObject(documentToJsonObject(document), schema);
    }

    public void validate(String json, PublicationSchema schema) {
        if (json == null || json.isBlank()) {
            throw new SchemaValidationException("JSON cannot be null or blank");
        }
        validateJsonObject(new JSONObject(json), schema);
    }

    private void validateJsonObject(JSONObject jsonObject, PublicationSchema schema) {
        log.debug("Validating against JSON schema: {}", schema.path());

        try {
            schemaCache.computeIfAbsent(schema, this::loadSchema).validate(jsonObject);
            log.debug("JSON schema validation passed");
        } catch (ValidationException e) {
            log.error("JSON schema validation failed");
            throw new SchemaValidationException(
                    "JSON schema validation failed:\n" + String.join("\n", e.getAllMessages()), e);
        } catch (IllegalArgumentException e) {
            log.error("Schema format not supported (everit supports draft-04)", e);
            throw new SchemaValidationException("JSON schema validation failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error during JSON schema validation", e);
            throw new SchemaValidationException("JSON schema validation failed: " + e.getMessage(), e);
        }
    }

    private Schema loadSchema(PublicationSchema publicationSchema) {
        ClassPathResource resource = new ClassPathResource(publicationSchema.path());
        if (!resource.exists()) {
            throw new IllegalStateException("Schema file not found: " + publicationSchema.path());
        }
        try (InputStream in = resource.getInputStream()) {
            JSONObject rawSchema = new JSONObject(new JSONTokener(in));
            Schema schema = SchemaLoader.builder()
                    .schemaJson(normalizeSchemaForDraft04(rawSchema))
                    .build()
                    .load()
                    .build();
            log.info("JSON schema loaded and cached from {}", publicationSchema.path());
            return schema;
        } catch (IOException e) {
            throw new SchemaValidationException(
                    "Failed to load JSON schema from " + publicationSchema.path() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Normalizes a draft 2020-12 schema so everit (draft-04 only) can load it.
     * Converts $defs → definitions and updates the $schema URI.
     */
    private JSONObject normalizeSchemaForDraft04(JSONObject rawSchema) {
        String json = rawSchema.toString();
        json = json.replace("https://json-schema.org/draft/2020-12/schema", "http://json-schema.org/draft-04/schema#");
        json = json.replace("\"$defs\"", "\"definitions\"");
        json = json.replace("#/$defs/", "#/definitions/");
        return new JSONObject(json);
    }

    private JSONObject documentToJsonObject(CourtListDocument document) {
        try {
            return new JSONObject(OBJECT_MAPPER.writeValueAsString(document));
        } catch (Exception e) {
            throw new SchemaValidationException("Failed to convert document to JSON: " + e.getMessage(), e);
        }
    }
}
