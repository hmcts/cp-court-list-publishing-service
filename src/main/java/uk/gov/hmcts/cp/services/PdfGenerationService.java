package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import uk.gov.hmcts.cp.config.CourtListPublishingSystemUserConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.UUID;

import static java.lang.String.format;

/**
 * Service for generating PDF files from payload data and uploading to Azure Blob Storage.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "azure.storage.enabled", havingValue = "true", matchIfMissing = false)
public class PdfGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfGenerationService.class);

    // Constants for document generator service
    private static final String BASE_URI_TEMPLATE = "/systemdocgenerator-command-api/command/api/rest/systemdocgenerator";
    private static final String TEMPLATE_NAME = "PublicCourtList";
    private static final String KEY_TEMPLATE_PAYLOAD = "templatePayload";
    private static final String KEY_CONVERSION_FORMAT = "conversionFormat";
    private static final String DOCUMENT_CONVERSION_FORMAT_PDF = "PDF";
    private static final String URL_ENDS_WITH = "/documents/generate";

    private final CourtListPublisherBlobClientService blobClientService;
    private final HttpClientFactory httpClientFactory;
    private final CourtListPublishingSystemUserConfig systemUserConfig;

    @Value("${common-platform-query-api.base-url}")
    private String commonPlatformQueryApiBaseUrl;

    /**
     * Generates a PDF file from the provided payload data, uploads it to Azure Blob Storage, and returns the SAS URL.
     */
    public String generateAndUploadPdf(JsonObject payload, UUID courtListId) throws IOException {
        LOGGER.info("Generating PDF for court list ID: {}", courtListId);
        
        byte[] pdfBytes;
        try {
            pdfBytes = generatePdfDocument(payload, TEMPLATE_NAME);
            LOGGER.info("Successfully generated PDF for court list ID: {}, size: {} bytes", 
                    courtListId, pdfBytes.length);
        } catch (Exception e) {
            LOGGER.error("Error generating PDF for court list ID: {}", courtListId, e);
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
        
        // Generate blob name and upload to Azure
        String blobName = generateBlobName(courtListId, "court-lists");
        long pdfSize = pdfBytes.length;
        ByteArrayInputStream pdfInputStream = new ByteArrayInputStream(pdfBytes);
        
        // Upload to Azure Blob Storage and get SAS URL
        String sasUrl = blobClientService.uploadPdfAndGenerateSasUrl(pdfInputStream, pdfSize, blobName);
        
        LOGGER.info("Successfully uploaded PDF to Azure for court list ID: {}. SAS URL generated", courtListId);
        return sasUrl;
    }

    /**
     * Generates a blob name for the PDF file
     */
    private String generateBlobName(UUID courtListId, String folderPath) {
        String fileName = (courtListId != null ? courtListId.toString() : "null") + ".pdf";
        if (folderPath != null && !folderPath.isEmpty()) {
            return folderPath + "/" + fileName;
        }
        return fileName;
    }

    /**
     * Gets the size of the generated PDF
     */
    public long getPdfSize(ByteArrayOutputStream pdfOutputStream) {
        return pdfOutputStream.size();
    }

    public byte[] generatePdfDocument(final JsonObject jsonData, final String templateIdentifier) throws IOException {
        try {
            RestTemplate restTemplate = httpClientFactory.getClient();
            ResponseEntity<byte[]> response = callDocumentGeneratorPDFService(restTemplate, jsonData, templateIdentifier);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new ResponseStatusException(
                HttpStatus.valueOf(response.getStatusCode().value()),
                format("Failed to generate document with identifier %s. Http status: %s",
                    templateIdentifier, response.getStatusCode())
            );
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            LOGGER.error("HTTP error generating PDF document with identifier: {}", templateIdentifier, e);
            throw new ResponseStatusException(
                HttpStatus.valueOf(e.getStatusCode().value()),
                format("Failed to generate document with identifier %s. Http status: %s, Http message: %s",
                    templateIdentifier, e.getStatusCode(), e.getResponseBodyAsString())
            );
        } catch (RestClientException e) {
            LOGGER.error("Rest client error generating PDF document with identifier: {}", templateIdentifier, e);
            throw new IOException(format("Failed to generate document with identifier %s: %s",
                templateIdentifier, e.getMessage()), e);
        }
    }

    private ResponseEntity<byte[]> callDocumentGeneratorPDFService(final RestTemplate restTemplate, final JsonObject jsonData,
                                                                   final String templateIdentifier) {

        JsonObject templatePayload = jsonData != null ? jsonData : Json.createObjectBuilder().build();

        final JsonObject payload = Json.createObjectBuilder()
                .add("templateName", templateIdentifier)
                .add(KEY_TEMPLATE_PAYLOAD, templatePayload)
                .add(KEY_CONVERSION_FORMAT, DOCUMENT_CONVERSION_FORMAT_PDF)
                .build();

        // Build the URL using the base URI template
        String URL = BASE_URI_TEMPLATE + URL_ENDS_WITH;
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(commonPlatformQueryApiBaseUrl)
                .path(URL);

        URI uri = uriBuilder.build().toUri();
        String systemUserId = systemUserConfig.getSystemUserId();
        if (systemUserId == null || systemUserId.isBlank()) {
            throw new IllegalStateException("COURTLISTPUBLISHING_SYSTEM_USER_ID is not configured");
        }
        // Set up headers (document generator uses system user)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("CJSCPPUID", systemUserId);

        // Convert JsonObject to JSON string
        String jsonPayload = jsonObjectToString(payload);

        // Create request entity
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

        LOGGER.info("Calling PDF service at {} with template identifier: {}", uri, templateIdentifier);

        // Make the REST call
        return restTemplate.exchange(
                uri,
            HttpMethod.POST,
            requestEntity,
            byte[].class
        );
    }

    /**
     * Converts JsonObject to JSON string
     */
    private String jsonObjectToString(JsonObject jsonObject) {
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = Json.createWriter(stringWriter)) {
            jsonWriter.write(jsonObject);
        }
        return stringWriter.toString();
    }
}
