package com.shoploc.shoplocorderservice.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Client HTTP vers le service catalogue, à l'adresse shoploc.catalog.base-url (définie dans le Config Server). */
@Configuration
public class CatalogClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public ProductCatalogClient httpProductCatalogClient(
            @Value("${shoploc.catalog.base-url:http://localhost:8081}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new HttpProductCatalogClient(RestClient.builder().requestFactory(requestFactory), baseUrl);
    }
}
