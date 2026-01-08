package uk.gov.hmcts.cp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

@Configuration
@Slf4j
public class RestTemplateConfig {

    @Value("${common-platform-query-api.ssl.verify:true}")
    private boolean sslVerify;

    @Bean
    public RestTemplate restTemplate() throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10 seconds
        factory.setReadTimeout(30000); // 30 seconds
        
        // If SSL verification is disabled (for development only)
        if (!sslVerify) {
            log.warn("⚠️  SSL certificate verification is DISABLED. This should only be used in development environments!");
            
            try {
                // Create a trust manager that accepts all certificates
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        
                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                            // Trust all certificates
                        }
                        
                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                            // Trust all certificates
                        }
                    }
                };
                
                // Install the all-trusting trust manager
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
                
                // Also disable hostname verification
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
                
                log.info("SSL verification disabled - all certificates will be trusted");
            } catch (Exception e) {
                log.error("Failed to configure SSL context: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to configure SSL context", e);
            }
        } else {
            log.info("SSL verification is enabled - using default truststore");
        }
        
        return new RestTemplate(factory);
    }
}

