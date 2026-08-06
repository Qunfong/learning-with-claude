package com.example.demo.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 7.1, 7.2 JWKS endpoint exposing the public RSA key for JWT verification.
 */
@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final RSAKey rsaKey;

    public JwksController(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    @GetMapping("/jwks.json")
    public ResponseEntity<String> jwks() {
        // Expose only the public key
        RSAKey publicKey = rsaKey.toPublicJWK();
        JWKSet jwkSet = new JWKSet(publicKey);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(jwkSet.toString());
    }
}
