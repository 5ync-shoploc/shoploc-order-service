package com.shoploc.shoplocorderservice.security;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documente, pour Swagger, les deux en-têtes que {@link CurrentUserArgumentResolver}
 * lit pour reconstruire le paramètre {@link CurrentUser} d'un endpoint (lui-même
 * caché dans le contrat via {@code @Parameter(hidden = true)}, pour éviter que
 * springdoc n'essaie de documenter ses champs comme des paramètres de requête).
 * <p>
 * Une seule annotation à poser sur chaque méthode de controller, plutôt que de
 * répéter les deux {@code @Parameter} un par un.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Parameters({
        @Parameter(name = CurrentUserArgumentResolver.USER_ID_HEADER, in = ParameterIn.HEADER, required = true,
                description = "Identifiant de l'utilisateur connecté (en-tête de confiance posé par la Gateway)",
                schema = @Schema(type = "string")),
        @Parameter(name = CurrentUserArgumentResolver.USER_ROLE_HEADER, in = ParameterIn.HEADER, required = true,
                description = "Rôle de l'utilisateur connecté",
                schema = @Schema(type = "string", allowableValues = {"CLIENT", "COMMERCANT"}))
})
public @interface CurrentUserHeaders {
}
