package com.example.demo.auth;

import com.example.demo.auth.service.OAuthClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 10.6 Integration tests for client registry.
 */
@SpringBootTest
class ClientRegistryIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OAuthClientService clientService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void register_validClient_returnsCredentials() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/oauth/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"My App","type":"confidential","redirectUris":["https://example.com/callback"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").exists())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("client_id");
    }

    @Test
    void register_invalidRedirectUri_returns400() throws Exception {
        mockMvc.perform(post("/admin/oauth/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad App","type":"public","redirectUris":["not-a-valid-uri"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void register_redirectUriWithFragment_returns400() throws Exception {
        mockMvc.perform(post("/admin/oauth/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Fragment App","type":"public","redirectUris":["https://example.com/callback#fragment"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void deactivate_existingClient_returns204() throws Exception {
        OAuthClientService.ClientRegistrationResult result =
                clientService.register("Deactivate Me", "public",
                        List.of("https://example.com/callback"));

        mockMvc.perform(delete("/admin/oauth/clients/" + result.clientId()))
                .andExpect(status().isNoContent());

        assertThat(clientService.findActiveClient(result.clientId())).isEmpty();
    }

    @Test
    void deactivate_nonexistentClient_returns404() throws Exception {
        mockMvc.perform(delete("/admin/oauth/clients/nonexistent-client-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatedClient_rejectsAuthorizationRequest() throws Exception {
        OAuthClientService.ClientRegistrationResult result =
                clientService.register("Soon Deactivated", "public",
                        List.of("https://example.com/callback"));

        clientService.deactivate(result.clientId());

        mockMvc.perform(get("/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", result.clientId())
                        .param("redirect_uri", "https://example.com/callback")
                        .param("code_challenge", "abc")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }
}
