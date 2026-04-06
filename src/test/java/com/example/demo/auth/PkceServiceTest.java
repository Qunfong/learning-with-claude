package com.example.demo.auth;

import com.example.demo.auth.service.PkceService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 10.1 Unit tests for PKCE verification logic.
 */
class PkceServiceTest {

    private final PkceService pkceService = new PkceService();

    @Test
    void validVerifier_passes() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = computeS256Challenge(verifier);

        assertThat(pkceService.verify(verifier, challenge, "S256")).isTrue();
    }

    @Test
    void tamperedVerifier_fails() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = computeS256Challenge(verifier);

        assertThat(pkceService.verify("wrong-verifier", challenge, "S256")).isFalse();
    }

    @Test
    void wrongMethod_fails() throws Exception {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = computeS256Challenge(verifier);

        assertThat(pkceService.verify(verifier, challenge, "plain")).isFalse();
    }

    @Test
    void emptyVerifier_fails() {
        assertThat(pkceService.verify("", "anything", "S256")).isFalse();
    }

    private String computeS256Challenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
