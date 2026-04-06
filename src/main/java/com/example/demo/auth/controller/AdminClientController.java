package com.example.demo.auth.controller;

import com.example.demo.auth.service.OAuthClientService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/oauth/clients")
public class AdminClientController {

    private final OAuthClientService clientService;

    public AdminClientController(OAuthClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            clientService.validateRedirectUris(req.redirectUris());
            OAuthClientService.ClientRegistrationResult result =
                    clientService.register(req.name(), req.type(), req.redirectUris());

            return ResponseEntity.ok(Map.of(
                    "client_id", result.clientId(),
                    "client_secret", result.clientSecret() != null ? result.clientSecret() : ""
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "error_description", e.getMessage()));
        }
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<?> deactivate(@PathVariable String clientId) {
        boolean found = clientService.deactivate(clientId);
        if (!found) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    record RegisterRequest(
            @NotBlank String name,
            @NotBlank String type,
            @NotEmpty List<String> redirectUris
    ) {}
}
