package com.example.demo.auth;

import com.example.demo.auth.service.OAuthClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 10.2 Integration test for full authorization code flow:
 * authorize → consent → code exchange → token validation
 */
@SpringBootTest
class OAuthFlowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OAuthClientService clientService;

    private MockMvc mockMvc;
    private String clientId;
    private static final String REDIRECT_URI = "https://example.com/callback";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        OAuthClientService.ClientRegistrationResult result =
                clientService.register("Test App", "confidential", List.of(REDIRECT_URI));
        clientId = result.clientId();
    }

    @Test
    void fullAuthorizationCodeFlow() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = computeS256(verifier);

        // Step 1: GET /oauth/authorize
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256")
                        .param("state", "xyz")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        // Step 2: POST /oauth/consent
        MvcResult consentResult = mockMvc.perform(post("/oauth/consent")
                        .param("user_id", "user-123")
                        .param("action", "grant")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = consentResult.getResponse().getHeader("Location");
        assertThat(location).contains("code=");
        assertThat(location).contains("state=xyz");

        String code = extractParam(location, "code");

        // Step 3: POST /oauth/token
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", clientId)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.refresh_token").exists());
    }

    @Test
    void missingCodeChallenge_redirectsWithError() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).contains("error=invalid_request");
    }

    @Test
    void unregisteredClient_returns400() throws Exception {
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", "unknown-client")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", "abc")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCodeVerifier_returnsInvalidGrant() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = computeS256(verifier);

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        MvcResult consentResult = mockMvc.perform(post("/oauth/consent")
                        .param("user_id", "user-123")
                        .param("action", "grant")
                        .session(session))
                .andReturn();

        String code = extractParam(consentResult.getResponse().getHeader("Location"), "code");

        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", clientId)
                        .param("code_verifier", "wrong-verifier"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void replayedCode_returnsInvalidGrant() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = computeS256(verifier);

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        MvcResult consentResult = mockMvc.perform(post("/oauth/consent")
                        .param("user_id", "user-123")
                        .param("action", "grant")
                        .session(session))
                .andReturn();

        String code = extractParam(consentResult.getResponse().getHeader("Location"), "code");

        // First exchange succeeds
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("client_id", clientId)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk());

        // Second exchange fails (replay)
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("client_id", clientId)
                        .param("code_verifier", verifier))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void consentDenied_redirectsWithAccessDenied() throws Exception {
        String challenge = computeS256("any-verifier");
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_challenge", challenge)
                        .param("code_challenge_method", "S256")
                        .param("state", "abc")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        MvcResult result = mockMvc.perform(post("/oauth/consent")
                        .param("user_id", "user-123")
                        .param("action", "deny")
                        .session(session))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        assertThat(location).contains("error=access_denied");
        assertThat(location).contains("state=abc");
    }

    private String computeS256(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String extractParam(String url, String param) {
        for (String part : url.split("[?&]")) {
            if (part.startsWith(param + "=")) return part.substring(param.length() + 1);
        }
        return null;
    }
}
