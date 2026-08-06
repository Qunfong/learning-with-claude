package com.example.demo.auth.controller;

import com.example.demo.auth.model.IntrospectResponse;
import com.example.demo.auth.service.OAuthClientService;
import com.example.demo.auth.service.TokenService;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 6.1 Token introspection endpoint (RFC 7662).
 * Caller must be a registered (active) resource server identified by client_id.
 */
@RestController
@RequestMapping("/oauth/introspect")
public class OAuthIntrospectController {

    private final TokenService tokenService;
    private final OAuthClientService clientService;

    public OAuthIntrospectController(TokenService tokenService, OAuthClientService clientService) {
        this.tokenService = tokenService;
        this.clientService = clientService;
    }

    @PostMapping(consumes = {"application/x-www-form-urlencoded", "application/json"})
    public ResponseEntity<IntrospectResponse> introspect(
            @RequestParam("token") String token,
            @RequestParam("client_id") String callerClientId) {

        // Validate that the caller is a registered active client (resource server)
        if (clientService.findActiveClient(callerClientId).isEmpty()) {
            return ResponseEntity.status(401).body(IntrospectResponse.inactive());
        }

        Optional<SignedJWT> jwtOpt = tokenService.validateAccessToken(token);
        if (jwtOpt.isEmpty()) {
            return ResponseEntity.ok(IntrospectResponse.inactive());
        }

        try {
            SignedJWT jwt = jwtOpt.get();
            String sub = jwt.getJWTClaimsSet().getSubject();
            String clientId = (String) jwt.getJWTClaimsSet().getClaim("client_id");
            long exp = jwt.getJWTClaimsSet().getExpirationTime().getTime() / 1000;
            return ResponseEntity.ok(new IntrospectResponse(true, sub, clientId, exp));
        } catch (Exception e) {
            return ResponseEntity.ok(IntrospectResponse.inactive());
        }
    }
}
