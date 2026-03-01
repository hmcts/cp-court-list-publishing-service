package uk.gov.hmcts.cp.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.services.pdf.PdfGenerationService;

@Configuration
public class PublicCourtListConfig {

    @Bean
    @ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
    public RestTemplate publicCourtListRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
    public RestTemplate documentGeneratorRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnProperty(name = "public-court-list.enabled", havingValue = "true")
    public PdfGenerationService pdfGenerationService(
            @Qualifier("documentGeneratorRestTemplate") final RestTemplate documentGeneratorRestTemplate,
            @Value("${public-court-list.document-generator.base-url}") final String documentGeneratorBaseUrl) {
        return new PdfGenerationService(documentGeneratorRestTemplate, documentGeneratorBaseUrl);
    }
}
