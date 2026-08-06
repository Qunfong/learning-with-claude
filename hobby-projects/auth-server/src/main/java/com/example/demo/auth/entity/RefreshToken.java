package com.example.demo.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token_hash", columnList = "token_hash")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "access_token_jti")
    private String accessTokenJti;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAccessTokenJti() { return accessTokenJti; }
    public void setAccessTokenJti(String accessTokenJti) { this.accessTokenJti = accessTokenJti; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
