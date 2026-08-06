package com.example.demo.auth.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class PkceService {

    /**
     * Verifies that BASE64URL(SHA256(verifier)) == challenge.
     */
    public boolean verify(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
        if (!"S256".equalsIgnoreCase(codeChallengeMethod)) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return computed.equals(codeChallenge);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}
