package com.fooddelivery.userservice.web;

import java.util.Locale;

import com.fooddelivery.userservice.entity.Role;
import com.fooddelivery.userservice.exception.AuthenticationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controller methods declare a {@link CallerInfo} parameter: the user of the request's verified access token
 * (its subject and "role" claim; SecurityConfig has checked the token). Fails with 401 when there is none.
 */
public class CallerInfoArgumentResolver implements HandlerMethodArgumentResolver {

    static final String ROLE_CLAIM = "role";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CallerInfo.class.equals(parameter.getParameterType());
    }

    @Override
    public CallerInfo resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("CallerInfo is only available in HTTP servlet requests");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AuthenticationFailedException("Sign in to use this API.");
        }
        Jwt jwt = token.getToken();
        return new CallerInfo(
                userIdOf(jwt.getSubject()),
                roleOf(jwt.getClaimAsString(ROLE_CLAIM)),
                // With server.forward-headers-strategy=native this is the real client IP, not the gateway's.
                request.getRemoteAddr(),
                MDC.get(RequestIdFilter.MDC_REQUEST_ID));
    }

    private static Long userIdOf(String subject) {
        try {
            long userId = Long.parseLong(subject);
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException e) {
            // refused below
        }
        throw new AuthenticationFailedException("The access token does not name a user.");
    }

    private static Role roleOf(String claim) {
        try {
            return Role.valueOf(String.valueOf(claim).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AuthenticationFailedException("The access token does not carry a known role.");
        }
    }
}
