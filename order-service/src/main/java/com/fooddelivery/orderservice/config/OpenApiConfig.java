package com.fooddelivery.orderservice.config;

import com.fooddelivery.orderservice.web.CallerInfo;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: http://localhost:8084/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    /** The access token of a sign-in (user-service /api/v1/auth/login), sent as Authorization: Bearer */
    private static final String BEARER = "bearer-token";

    static {
        // CallerInfo is resolved from the access token, not from parameters: hide it from the docs.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CallerInfo.class);
    }

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .info(new Info()
                .title("Order Service API")
                .version("v1")
                .description("Cart, order placement (idempotent), order tracking and the order lifecycle. "
                        + "Send the access token of a sign-in: the Authorize button, Bearer token."));
    }
}
