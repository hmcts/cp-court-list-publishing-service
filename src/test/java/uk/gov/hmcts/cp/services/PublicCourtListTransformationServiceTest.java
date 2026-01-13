package uk.gov.hmcts.cp.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.cp.models.CourtListPayload;
import uk.gov.hmcts.cp.models.transformed.CourtListDocument;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PublicCourtListTransformationServiceTest {

    @InjectMocks
    private PublicCourtListTransformationService transformationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CourtListPayload payload;

    @BeforeEach
    void setUp() throws Exception {
        payload = loadPayloadFromStubData("stubdata/court-list-payload-public.json");
    }

    @Test
    void transform_shouldTransformToSimplifiedFormat() throws Exception {
        // When
        CourtListDocument document = transformationService.transform(payload);

        // Then - Convert to JSON and compare with expected
        String actualJson = objectMapper.writeValueAsString(document);
        JsonNode actualNode = objectMapper.readTree(actualJson);
        
        // Load expected output and replace dynamic printdate
        String expectedJson = loadStubData("stubdata/expected-court-list-document-public.json");
        String actualPrintDate = actualNode.get("document").get("data").get("job").get("printdate").asText();
        expectedJson = expectedJson.replace("{{PRINTDATE}}", actualPrintDate);
        JsonNode expectedNode = objectMapper.readTree(expectedJson);
        
        assertThat(actualNode).isEqualTo(expectedNode);
    }

    private CourtListPayload loadPayloadFromStubData(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String json = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return objectMapper.readValue(json, CourtListPayload.class);
    }

    private String loadStubData(String resourcePath) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
