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
 * 10.4 Integration tests for token revocation and introspection.
 */
@SpringBootTest
class TokenRevocationIntegrationTest {

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
                clientService.register("Revocation Test", "confidential",
                        List.of("https://example.com/callback"));
        clientId = result.clientId();
    }

    @Test
    void revoke_validToken_returns200() throws Exception {
        var pair = tokenService.issueTokenPair("user-a", clientId);

        mockMvc.perform(post("/oauth/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", pair.refreshToken()))
                .andExpect(status().isOk());
    }

    @Test
    void revoke_unknownToken_returns200Silently() throws Exception {
        mockMvc.perform(post("/oauth/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "nonexistent-token"))
                .andExpect(status().isOk());
    }

    @Test
    void introspect_activeToken_returnsActiveClaims() throws Exception {
        var pair = tokenService.issueTokenPair("user-b", clientId);

        mockMvc.perform(post("/oauth/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", pair.accessToken())
                        .param("client_id", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").value("user-b"))
                .andExpect(jsonPath("$.client_id").value(clientId))
                .andExpect(jsonPath("$.exp").exists());
    }

    @Test
    void introspect_invalidToken_returnsInactive() throws Exception {
        mockMvc.perform(post("/oauth/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "invalid.token.here")
                        .param("client_id", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void introspect_unregisteredCaller_returns401() throws Exception {
        var pair = tokenService.issueTokenPair("user-c", clientId);

        mockMvc.perform(post("/oauth/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", pair.accessToken())
                        .param("client_id", "unknown-caller"))
                .andExpect(status().isUnauthorized());
    }
}
