package com.shoploc.shoplocorderservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare le contrat d'API (titre affiché dans Swagger UI).
 * Swagger UI : /swagger-ui.html — JSON du contrat : /v3/api-docs.
 *
 * Pas de schéma d'authentification ici : ce service ne vérifie pas de JWT.
 * Chaque endpoint lit X-User-Id / X-User-Role via CurrentUserArgumentResolver
 * (en-têtes de confiance, documentés par @CurrentUserHeaders — voir CLAUDE.md local).
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "ShopLoc – Order Service",
        version = "v1",
        description = "Panier et commandes (vue client et vue commerçant)."))
public class OpenApiConfig {
}
