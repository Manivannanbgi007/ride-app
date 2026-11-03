package com.fooddelivery.aimcpclient.web;

import com.fooddelivery.aimcpclient.identity.CallerIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lets controller methods declare a {@link CallerIdentity} parameter: the caller of the verified access token of this
 * request (see {@link CallerIdentityResolver}).
 */
@Component
@RequiredArgsConstructor
public class CallerIdentityArgumentResolver implements HandlerMethodArgumentResolver {

    private final CallerIdentityResolver identityResolver;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CallerIdentity.class.equals(parameter.getParameterType());
    }

    @Override
    public CallerIdentity resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = authentication != null && authentication.getPrincipal() instanceof Jwt token ? token : null;
        return identityResolver.resolve(jwt);
    }
}
