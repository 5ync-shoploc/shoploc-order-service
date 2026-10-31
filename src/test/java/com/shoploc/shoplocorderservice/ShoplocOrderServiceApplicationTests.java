package com.shoploc.shoplocorderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShoplocOrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}