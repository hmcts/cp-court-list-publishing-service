package uk.gov.hmcts.cp.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.domain.DtsMeta;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;

/**
 * Service for making REST API calls using RestEasy client.
 * 
 * <p>This service provides methods to send HTTP POST requests to external APIs,
 * with support for Azure managed identity authentication and metadata headers.
 */
@Service
@Slf4j
public class RestEasyClientService {

    public static final String OCP_APIM_SUBSCRIPTION_KEY = "Ocp-Apim-Subscription-Key";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_TOKEN = "Bearer %s";
    public static final String EXTERNAL_SERVICE_ACCESS_TOKEN = "external-service-access-token";
    public static final String OCP_APIM_TRACE = "Ocp-Apim-Trace";
    public static final String TRUE = "true";
    public static final String X_PROVENANCE = "x-provenance";
    public static final String X_TYPE = "x-type";
    public static final String X_LIST_TYPE = "x-list-type";
    public static final String X_COURT_ID = "x-court-id";
    public static final String X_CONTENT_DATE = "x-content-date";
    public static final String X_LANGUAGE = "x-language";
    public static final String X_SENSITIVITY = "x-sensitivity";
    public static final String X_DISPLAY_FROM = "x-display-from";
    public static final String X_DISPLAY_TO = "x-display-to";


    //Make it consistant. Why restEasy here and restTemplate at other places? Use the same client everywhere LPT-2033
    @Value("${publishing.rest-client.connection-pool-size:10}")
    private String restEasyClientConnectionPoolSize;

    private ResteasyClient client;

    @PostConstruct
    public void createClient() {
        client = new ResteasyClientBuilderImpl()
                .disableTrustManager()
                .connectionPoolSize(Integer.parseInt(restEasyClientConnectionPoolSize))
                .build();
    }

    /**
     * Sends a POST request with Azure managed identity tokens and metadata headers.
     * 
     * @param url the target URL
     * @param payload the request payload
     * @param localServiceAccessToken the local service access token
     * @param remoteServiceAccessToken the remote service access token
     * @param meta the metadata for the request
     * @return the HTTP response
     */
    public Response post(final String url, final String payload, 
                        final String localServiceAccessToken, 
                        final String remoteServiceAccessToken, 
                        final DtsMeta meta) {
        final Invocation.Builder request = this.client.target(url).request();
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        getHeaders(localServiceAccessToken, remoteServiceAccessToken, meta)
                .forEach(headers::add);
        request.headers(headers);
        return request.post(Entity.json(payload));
    }

    private Map<String, String> getHeaders(final String localServiceAccessToken, 
                                          final String remoteServiceAccessToken, 
                                          final DtsMeta meta) {
        return ImmutableMap.<String, String>builder()
                .put(AUTHORIZATION, String.format(BEARER_TOKEN, localServiceAccessToken))
                .put("Accept", MediaType.APPLICATION_JSON)
                .put(EXTERNAL_SERVICE_ACCESS_TOKEN, remoteServiceAccessToken)
                .put(OCP_APIM_TRACE, TRUE)
                .put(X_PROVENANCE, meta.getProvenance())
                .put(X_TYPE, meta.getType())
                .put(X_LIST_TYPE, meta.getListType())
                .put(X_COURT_ID, meta.getCourtId())
                .put(X_CONTENT_DATE, meta.getContentDate())
                .put(X_LANGUAGE, meta.getLanguage())
                .put(X_SENSITIVITY, meta.getSensitivity())
                .put(X_DISPLAY_FROM, meta.getDisplayFrom())
                .put(X_DISPLAY_TO, meta.getDisplayTo())
                .build();
    }
}
