package com.example.demo.auth.controller;

import com.example.demo.auth.entity.AuthorizationCode;
import com.example.demo.auth.entity.OAuthClient;
import com.example.demo.auth.entity.RefreshToken;
import com.example.demo.auth.model.TokenResponse;
import com.example.demo.auth.repository.RefreshTokenRepository;
import com.example.demo.auth.service.AuthorizationService;
import com.example.demo.auth.service.OAuthClientService;
import com.example.demo.auth.service.PkceService;
import com.example.demo.auth.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/oauth/token")
public class OAuthTokenController {

    private final OAuthClientService clientService;
    private final AuthorizationService authorizationService;
    private final TokenService tokenService;
    private final PkceService pkceService;
    private final RefreshTokenRepository refreshTokenRepository;

    public OAuthTokenController(OAuthClientService clientService,
                                AuthorizationService authorizationService,
                                TokenService tokenService,
                                PkceService pkceService,
                                RefreshTokenRepository refreshTokenRepository) {
        this.clientService = clientService;
        this.authorizationService = authorizationService;
        this.tokenService = tokenService;
        this.pkceService = pkceService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @PostMapping(consumes = {"application/x-www-form-urlencoded", "application/json"})
    public ResponseEntity<?> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier,
            @RequestParam(value = "refresh_token", required = false) String refreshTokenRaw) {

        return switch (grantType) {
            case "authorization_code" -> handleAuthorizationCode(code, redirectUri, clientId, codeVerifier);
            case "refresh_token" -> handleRefreshToken(refreshTokenRaw, clientId);
            default -> ResponseEntity.badRequest().body(
                    Map.of("error", "unsupported_grant_type"));
        };
    }

    /**
     * 5.1, 5.3, 5.4, 5.5
     */
    private ResponseEntity<?> handleAuthorizationCode(String code, String redirectUri,
                                                      String clientId, String codeVerifier) {
        if (code == null || clientId == null || codeVerifier == null) {
            return error("invalid_request", "Missing required parameters");
        }

        // Validate client is active
        Optional<OAuthClient> clientOpt = clientService.findActiveClient(clientId);
        if (clientOpt.isEmpty()) {
            return error("invalid_client", "Unknown or inactive client");
        }

        // Atomically claim the code (5.5 replay protection)
        Optional<AuthorizationCode> codeOpt = authorizationService.claimCode(code);
        if (codeOpt.isEmpty()) {
            // Code may have been replayed — detect double-use scenario
            // If code exists but already used, invalidate derived tokens
            return error("invalid_grant", "Authorization code is invalid, expired, or already used");
        }

        AuthorizationCode authCode = codeOpt.get();

        // Validate client_id matches
        if (!authCode.getClientId().equals(clientId)) {
            return error("invalid_grant", "client_id mismatch");
        }

        // Validate redirect_uri matches
        if (redirectUri != null && !authCode.getRedirectUri().equals(redirectUri)) {
            return error("invalid_grant", "redirect_uri mismatch");
        }

        // 5.2 verify PKCE
        if (!pkceService.verify(codeVerifier, authCode.getCodeChallenge(), authCode.getCodeChallengeMethod())) {
            return error("invalid_grant", "PKCE verification failed");
        }

        try {
            TokenResponse tokenResponse = tokenService.issueTokenPair(authCode.getUserId(), clientId);
            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "server_error"));
        }
    }

    /**
     * 5.2
     */
    private ResponseEntity<?> handleRefreshToken(String rawToken, String clientId) {
        if (rawToken == null) {
            return error("invalid_request", "refresh_token is required");
        }

        String tokenHash = tokenService.hashToken(rawToken);
        Optional<RefreshToken> rtOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (rtOpt.isEmpty()) {
            return error("invalid_grant", "Invalid refresh token");
        }

        RefreshToken rt = rtOpt.get();

        if (rt.isRevoked()) {
            return error("invalid_grant", "Refresh token has been revoked");
        }

        if (rt.getExpiresAt().isBefore(Instant.now())) {
            return error("invalid_grant", "Refresh token has expired");
        }

        // Validate client_id if provided
        if (clientId != null && !rt.getClientId().equals(clientId)) {
            return error("invalid_grant", "client_id mismatch");
        }

        Optional<OAuthClient> clientOpt = clientService.findActiveClient(rt.getClientId());
        if (clientOpt.isEmpty()) {
            return error("invalid_client", "Client is no longer active");
        }

        try {
            TokenResponse tokenResponse = tokenService.issueTokenPair(rt.getUserId(), rt.getClientId());
            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "server_error"));
        }
    }

    private ResponseEntity<?> error(String error, String description) {
        return ResponseEntity.badRequest().body(Map.of("error", error, "error_description", description));
    }
}
