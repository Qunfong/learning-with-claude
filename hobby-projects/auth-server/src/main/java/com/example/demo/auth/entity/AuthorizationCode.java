package com.example.demo.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "authorization_codes", indexes = {
    @Index(name = "idx_auth_code", columnList = "code")
})
public class AuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;

    @Column(name = "code_challenge", nullable = false)
    private String codeChallenge;

    @Column(name = "code_challenge_method", nullable = false)
    private String codeChallengeMethod;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getCodeChallenge() { return codeChallenge; }
    public void setCodeChallenge(String codeChallenge) { this.codeChallenge = codeChallenge; }
    public String getCodeChallengeMethod() { return codeChallengeMethod; }
    public void setCodeChallengeMethod(String codeChallengeMethod) { this.codeChallengeMethod = codeChallengeMethod; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
