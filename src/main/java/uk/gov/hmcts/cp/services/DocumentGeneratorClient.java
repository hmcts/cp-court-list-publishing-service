package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.cp.config.CourtListPublishingSystemUserConfig;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;

import static java.lang.String.format;

/**
 * Shared client for the document generator render API.
 * Used by court list publishing (generate + upload) and by the public court list endpoint (generate only).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${azure.storage.enabled:false} or ${public-court-list.enabled:false}")
public class DocumentGeneratorClient {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentGeneratorClient.class);
    private static final String RENDER_PATH = "/systemdocgenerator-command-api/command/api/rest/systemdocgenerator/render";
    private static final String KEY_TEMPLATE_PAYLOAD = "templatePayload";
    private static final String KEY_CONVERSION_FORMAT = "conversionFormat";
    private static final String DOCUMENT_CONVERSION_FORMAT_PDF = "pdf";
    private static final String RENDER_MEDIA_TYPE = "application/vnd.systemdocgenerator.render+json";

    private final HttpClientFactory httpClientFactory;
    private final CourtListPublishingSystemUserConfig systemUserConfig;

    @Value("${common-platform-query-api.base-url}")
    private String commonPlatformQueryApiBaseUrl;

    /**
     * Calls the document generator to produce a PDF from the given template payload and template name.
     *
     * @param templatePayload the payload for the template (court list data etc.)
     * @param templateName    the document generator template identifier
     * @return PDF bytes, never null or empty
     * @throws IOException if the call fails or response is empty
     */
    public byte[] generatePdf(final JsonObject templatePayload, final String templateName) throws IOException {
        RestTemplate restTemplate = httpClientFactory.getClient();
        try {
            ResponseEntity<byte[]> response = callRender(restTemplate, templatePayload, templateName);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().length > 0) {
                return response.getBody();
            }
            throw new ResponseStatusException(
                    response.getStatusCode(),
                    format("Failed to generate document with identifier %s. Http status: %s",
                            templateName, response.getStatusCode())
            );
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            LOG.error("HTTP error generating PDF with template: {}", templateName, e);
            throw new ResponseStatusException(
                    HttpStatus.valueOf(e.getStatusCode().value()),
                    format("Failed to generate document with identifier %s: %s",
                            templateName, e.getResponseBodyAsString()),
                    e
            );
        } catch (RestClientException e) {
            LOG.error("Rest client error generating PDF with template: {}", templateName, e);
            throw new IOException(format("Failed to generate document with identifier %s: %s",
                    templateName, e.getMessage()), e);
        }
    }

    private ResponseEntity<byte[]> callRender(final RestTemplate restTemplate, final JsonObject templatePayload,
                                                final String templateName) {
        JsonObject payload = Json.createObjectBuilder()
                .add("templateName", templateName)
                .add(KEY_TEMPLATE_PAYLOAD, templatePayload != null ? templatePayload : Json.createObjectBuilder().build())
                .add(KEY_CONVERSION_FORMAT, DOCUMENT_CONVERSION_FORMAT_PDF)
                .build();

        URI uri = UriComponentsBuilder
                .fromUriString(commonPlatformQueryApiBaseUrl)
                .path(RENDER_PATH)
                .build()
                .toUri();

        String systemUserId = systemUserConfig.getSystemUserId();
        if (systemUserId == null || systemUserId.isBlank()) {
            throw new IllegalStateException("COURTLISTPUBLISHING_SYSTEM_USER_ID is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(RENDER_MEDIA_TYPE));
        headers.set("CJSCPPUID", systemUserId);

        String jsonPayload = jsonObjectToString(payload);
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

        LOG.info("Calling document generator at {} with template: {}", uri, templateName);
        return restTemplate.exchange(uri, HttpMethod.POST, requestEntity, byte[].class);
    }

    private static String jsonObjectToString(final JsonObject jsonObject) {
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = Json.createWriter(stringWriter)) {
            jsonWriter.write(jsonObject);
        }
        return stringWriter.toString();
    }
}
