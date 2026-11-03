package com.fooddelivery.userservice.controller;

import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public key that checks access-token signatures, in the standard JWK Set format. The API Gateway and the services
 * download it once and cache it ({@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}).
 * It is called service to service only: the gateway does not route it.
 */
@Tag(name = "Auth")
@RestController
public class JwksController {

    private final Map<String, Object> publicKeys;

    public JwksController(RSAKey jwtSigningKey) {
        // toPublicJWK() drops every private part of the key
        this.publicKeys = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }

    @Operation(summary = "Public key(s) for checking access tokens (JWK Set)")
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(publicKeys);
    }
}
