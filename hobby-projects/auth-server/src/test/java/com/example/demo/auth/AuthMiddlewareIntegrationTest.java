package com.example.demo.auth;

import com.example.demo.auth.service.OAuthClientService;
import com.example.demo.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 10.5 Integration tests for auth middleware.
 * Tests run with AUTH_ENFORCE=true to check rejection behavior.
 */
@SpringBootTest
@TestPropertySource(properties = "AUTH_ENFORCE=true")
class AuthMiddlewareIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OAuthClientService clientService;

    @Autowired
    private TokenService tokenService;

    private MockMvc mockMvc;
    private String clientId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        OAuthClientService.ClientRegistrationResult result =
                clientService.register("Middleware Test", "confidential",
                        List.of("https://example.com/callback"));
        clientId = result.clientId();
    }

    @Test
    void protectedRoute_withValidToken_isAllowed() throws Exception {
        var pair = tokenService.issueTokenPair("user-1", clientId);

        mockMvc.perform(get("/hello")
                        .header("Authorization", "Bearer " + pair.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void protectedRoute_withMissingToken_returns401() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_token"));
    }

    @Test
    void protectedRoute_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/hello")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    @Test
    void publicRoute_health_isAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoute_oauthEndpoints_areExempt() throws Exception {
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "test")
                        .param("redirect_uri", "https://example.com")
                        .param("code_challenge", "abc")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest()); // Bad request due to invalid client, NOT 401
    }

    @Test
    void publicRoute_jwks_isAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk());
    }
}
