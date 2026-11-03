package com.fooddelivery.aimcpserver.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Shared setup of the MOCKED context tests: one context (fake model, in-memory proposals) and one stub gateway. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(FakeBeans.class)
public abstract class ContextTestSupport {

    protected static final StubGateway GATEWAY = new StubGateway();

    @DynamicPropertySource
    static void gatewayUrl(DynamicPropertyRegistry registry) {
        registry.add("app.gateway.base-url", GATEWAY::url);
        registry.add("app.jwt.jwk-set-uri", TestJwts::jwksUri);
    }
}
