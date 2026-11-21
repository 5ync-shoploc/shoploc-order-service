package com.shoploc.shoplocorderservice.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Remplace le client HTTP du catalogue par le faux catalogue, pour les tests d'intégration. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeCatalogConfiguration {

    @Bean
    @Primary
    public FakeProductCatalogClient fakeProductCatalogClient() {
        return new FakeProductCatalogClient();
    }
}
