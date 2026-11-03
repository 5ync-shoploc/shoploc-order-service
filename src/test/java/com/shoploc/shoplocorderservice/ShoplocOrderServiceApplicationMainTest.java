package com.shoploc.shoplocorderservice;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class ShoplocOrderServiceApplicationMainTest {

    @Test
    void mainStartsSpringApplication() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            String[] args = {};

            ShoplocOrderServiceApplication.main(args);

            spring.verify(() -> SpringApplication.run(eq(ShoplocOrderServiceApplication.class), any(String[].class)));
        }
    }
}