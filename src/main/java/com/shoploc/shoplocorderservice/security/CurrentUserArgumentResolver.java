package com.shoploc.shoplocorderservice.security;

import com.shoploc.shoplocorderservice.exception.ForbiddenException;
import com.shoploc.shoplocorderservice.exception.UnauthenticatedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * IDENTITÉ SIMULÉE, en attendant Keycloak.
 * Lit l'utilisateur dans les en-têtes X-User-Id et X-User-Role.
 * Quand Keycloak sera branché, seule cette classe sera remplacée par la lecture du token JWT :
 * les controllers et le service ne changeront pas.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

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
            return new CurrentUser(userId, Role.valueOf(role.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ForbiddenException("Unknown role: " + role);
        }
    }
}
