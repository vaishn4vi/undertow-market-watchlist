package com.undertow.config;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.undertow.auth.service.AuthService;
import com.undertow.common.exception.UnauthorizedException;

/**
 * Resolves the current user's externalId from a validated Authorization
 * bearer token - this is the ONLY place in the app that turns "who is
 * making this request" into an identity every other service trusts.
 *
 * Previously this trusted a client-supplied X-Demo-User-Id header (or fell
 * back to a single shared "demo-user-1" default), which is why every
 * visitor to the deployed app shared one account. There is deliberately no
 * fallback anymore: a missing, malformed, unknown, or expired token always
 * results in a 401, never a guess.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuthService authService;

    public CurrentUserArgumentResolver(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(String.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                   ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest,
                                   WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String header = request != null ? request.getHeader("Authorization") : null;

        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("Authentication required.");
        }

        String token = header.substring("Bearer ".length());
        return authService.resolveExternalIdFromToken(token)
                .orElseThrow(() -> new UnauthorizedException("Session expired. Please log in again."));
    }
}
