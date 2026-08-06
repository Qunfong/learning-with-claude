package com.example.demo.auth;

import com.example.demo.auth.service.OAuthClientService;
import com.example.demo.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 10.3 Integration tests for refresh token flow.
 */
@SpringBootTest
class RefreshTokenIntegrationTest {

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
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        OAuthClientService.ClientRegistrationResult result =
                clientService.register("Refresh Test App", "confidential",
                        List.of("https://example.com/callback"));
        clientId = result.clientId();
    }

    @Test
    void validRefreshToken_issuesNewAccessToken() throws Exception {
        var tokenPair = tokenService.issueTokenPair("user-refresh-test", clientId);

        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", tokenPair.refreshToken())
                        .param("client_id", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void invalidRefreshToken_returnsInvalidGrant() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", "completely-invalid-token")
                        .param("client_id", clientId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void revokedRefreshToken_returnsInvalidGrant() throws Exception {
        var tokenPair = tokenService.issueTokenPair("user-revoked", clientId);
        String rawRefreshToken = tokenPair.refreshToken();

        mockMvc.perform(post("/oauth/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", rawRefreshToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", rawRefreshToken)
                        .param("client_id", clientId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }
}
