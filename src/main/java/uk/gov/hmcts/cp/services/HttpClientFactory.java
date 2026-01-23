package uk.gov.hmcts.cp.services;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpClientFactory {

    private final RestTemplate restTemplate;

    public HttpClientFactory(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public RestTemplate getClient() {
        return restTemplate;
    }
}
