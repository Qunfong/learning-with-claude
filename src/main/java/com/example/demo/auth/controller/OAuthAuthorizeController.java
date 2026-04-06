package com.example.demo.auth.controller;

import com.example.demo.auth.entity.OAuthClient;
import com.example.demo.auth.service.AuthorizationService;
import com.example.demo.auth.service.OAuthClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/oauth")
public class OAuthAuthorizeController {

    private static final String SESSION_ATTR = "oauth_pending_request";

    private final OAuthClientService clientService;
    private final AuthorizationService authorizationService;

    public OAuthAuthorizeController(OAuthClientService clientService,
                                    AuthorizationService authorizationService) {
        this.clientService = clientService;
        this.authorizationService = authorizationService;
    }

    /**
     * 4.1 Validate authorization request and store in session.
     */
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            @RequestParam(value = "state", required = false) String state,
            HttpSession session) {

        // Validate client — do NOT redirect on client errors
        Optional<OAuthClient> clientOpt = clientService.findActiveClient(clientId);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "invalid_client", "error_description", "Unknown or inactive client"));
        }

        OAuthClient client = clientOpt.get();

        // Validate redirect_uri — do NOT redirect on URI mismatch
        if (!clientService.isValidRedirectUri(client, redirectUri)) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "invalid_request", "error_description", "redirect_uri not in client allowlist"));
        }

        // Validate response_type
        if (!"code".equals(responseType)) {
            return redirectError(redirectUri, "unsupported_response_type", state);
        }

        // PKCE is required
        if (codeChallenge == null || codeChallenge.isBlank()) {
            return redirectError(redirectUri, "invalid_request", state);
        }

        // Store pending request in session
        session.setAttribute(SESSION_ATTR, new PendingAuthRequest(
                clientId, redirectUri, codeChallenge,
                codeChallengeMethod != null ? codeChallengeMethod : "S256",
                state));

        // In a real app this would redirect to a login/consent page
        // For demo, redirect to a consent endpoint
        return ResponseEntity.status(302)
                .header("Location", "/oauth/consent")
                .build();
    }

    /**
     * 4.2 / 4.3 / 4.4 Consent endpoint — grant or deny.
     * In production this would be a form; here we accept a query param for testing.
     */
    @PostMapping("/consent")
    public ResponseEntity<?> consent(
            @RequestParam("user_id") String userId,
            @RequestParam("action") String action,
            HttpSession session) {

        PendingAuthRequest pending = (PendingAuthRequest) session.getAttribute(SESSION_ATTR);
        if (pending == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "invalid_request", "error_description", "No pending authorization request"));
        }
        session.removeAttribute(SESSION_ATTR);

        if ("deny".equals(action)) {
            return buildRedirect(pending.redirectUri(), "error", "access_denied", pending.state());
        }

        String code = authorizationService.issueAuthorizationCode(
                pending.clientId(), userId, pending.redirectUri(),
                pending.codeChallenge(), pending.codeChallengeMethod());

        String location = UriComponentsBuilder.fromUriString(pending.redirectUri())
                .queryParam("code", code)
                .queryParamIfPresent("state", Optional.ofNullable(pending.state()))
                .build().toUriString();

        return ResponseEntity.status(302).header("Location", location).build();
    }

    private ResponseEntity<?> redirectError(String redirectUri, String error, String state) {
        return buildRedirect(redirectUri, "error", error, state);
    }

    private ResponseEntity<?> buildRedirect(String redirectUri, String paramKey, String paramValue, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam(paramKey, paramValue);
        if (state != null) builder.queryParam("state", state);
        return ResponseEntity.status(302).header("Location", builder.build().toUriString()).build();
    }

    record PendingAuthRequest(String clientId, String redirectUri, String codeChallenge,
                              String codeChallengeMethod, String state) implements java.io.Serializable {}
}
