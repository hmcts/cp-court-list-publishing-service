package uk.gov.hmcts.cp.services;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
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
    private static final String BASE_URI_TEMPLATE = "http://localhost:%s/systemdocgenerator-command-api/command/api/rest/systemdocgenerator";
    private static final String TEMPLATE_NAME = "PublicCourtList";
    private static final String KEY_TEMPLATE_PAYLOAD = "templatePayload";
    private static final String KEY_CONVERSION_FORMAT = "conversionFormat";
    private static final String DOCUMENT_CONVERSION_FORMAT_PDF = "PDF";
    private static final String USER_ID_HEADER = "userId";
    private static final String URL_ENDS_WITH = "/documents/generate";

    private final CourtListPublisherBlobClientService blobClientService;
    private final HttpClientFactory httpClientFactory;

    @Value("${server.port:8082}")
    private int serverPort;

    /**
     * Generates a PDF file from the provided payload data, uploads it to Azure Blob Storage, and returns the SAS URL
     */
    public String generateAndUploadPdf(JsonObject payload, UUID courtListId, UUID userId) throws IOException {
        LOGGER.info("Generating PDF for court list ID: {}", courtListId);
        
        // Use system user if userId not provided
        UUID systemUserId = userId != null ? userId : getSystemUserId();

        // Generate PDF using document generator service
        byte[] pdfBytes;
        try {
            pdfBytes = generatePdfDocument(payload, TEMPLATE_NAME, systemUserId);
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
     * Gets system user ID for document generation
     */
    private UUID getSystemUserId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
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

    public byte[] generatePdfDocument(final JsonObject jsonData, final String templateIdentifier, final UUID userId) throws IOException {
        try {
            RestTemplate restTemplate = httpClientFactory.getClient();
            ResponseEntity<byte[]> response = callDocumentGeneratorPDFService(restTemplate, jsonData, templateIdentifier, userId);

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
                                                                   final String templateIdentifier, final UUID userId) {

        JsonObject templatePayload = jsonData != null ? jsonData : Json.createObjectBuilder().build();

        final JsonObject payload = Json.createObjectBuilder()
                .add("templateName", templateIdentifier)
                .add(KEY_TEMPLATE_PAYLOAD, templatePayload)
                .add(KEY_CONVERSION_FORMAT, DOCUMENT_CONVERSION_FORMAT_PDF)
                .build();

        // Build the URL using the base URI template
        String url = createBaseUri() + URL_ENDS_WITH;

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(USER_ID_HEADER, userId.toString());

        // Convert JsonObject to JSON string
        String jsonPayload = jsonObjectToString(payload);

        // Create request entity
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

        LOGGER.info("Calling PDF service at {} with template identifier: {}", url, templateIdentifier);

        // Make the REST call
        return restTemplate.exchange(
            url,
            HttpMethod.POST,
            requestEntity,
            byte[].class
        );
    }

    /**
     * Creates the base URI for the document generator service
     */
    private String createBaseUri() {
        return format(BASE_URI_TEMPLATE, serverPort);
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
