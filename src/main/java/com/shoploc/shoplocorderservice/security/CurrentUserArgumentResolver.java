package com.shoploc.shoplocorderservice.security;

import com.shoploc.shoplocorderservice.exception.ForbiddenException;
import com.shoploc.shoplocorderservice.exception.UnauthenticatedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Lit l'utilisateur dans les en-têtes X-User-Id, X-User-Role et X-User-Name (facultatif).
 * Le nom est encodé comme dans une URL (encodeURIComponent côté front) pour transporter les accents.
 * Quand Keycloak sera branché, seule cette classe sera remplacée par la lecture du token JWT :
 * les controllers et le service ne changeront pas.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String USER_NAME_HEADER = "X-User-Name";
    private static final int MAX_NAME_LENGTH = 200;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(USER_ID_HEADER);
        String role = webRequest.getHeader(USER_ROLE_HEADER);

        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            throw new UnauthenticatedException("Missing " + USER_ID_HEADER + " or " + USER_ROLE_HEADER + " header");
        }
        try {
            return new CurrentUser(userId, Role.valueOf(role.trim().toUpperCase()), decodeName(webRequest.getHeader(USER_NAME_HEADER)));
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Unknown role: " + role);
        }
    }

    /** Nom décodé, nettoyé et tronqué ; null s'il est absent ou illisible (le nom n'est jamais bloquant). */
    static String decodeName(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            String name = URLDecoder.decode(header, StandardCharsets.UTF_8).strip();
            if (name.isEmpty()) {
                return null;
            }
            return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
