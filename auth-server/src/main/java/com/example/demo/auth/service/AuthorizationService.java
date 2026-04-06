package com.example.demo.auth.service;

import com.example.demo.auth.entity.AuthorizationCode;
import com.example.demo.auth.repository.AuthorizationCodeRepository;
import com.example.demo.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthorizationService {

    private final AuthorizationCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthorizationService(AuthorizationCodeRepository codeRepository,
                                RefreshTokenRepository refreshTokenRepository) {
        this.codeRepository = codeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public String issueAuthorizationCode(String clientId, String userId, String redirectUri,
                                         String codeChallenge, String codeChallengeMethod) {
        String code = UUID.randomUUID().toString().replace("-", "");

        AuthorizationCode authCode = new AuthorizationCode();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId(userId);
        authCode.setRedirectUri(redirectUri);
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod(codeChallengeMethod);
        authCode.setExpiresAt(Instant.now().plusSeconds(600)); // 10 minutes
        codeRepository.save(authCode);

        return code;
    }

    /**
     * Atomically claims the authorization code; returns empty if already used, expired, or not found.
     */
    @Transactional
    public Optional<AuthorizationCode> claimCode(String code) {
        return codeRepository.findByCode(code)
                .filter(c -> !c.isUsed() && c.getExpiresAt().isAfter(Instant.now()))
                .map(c -> {
                    c.setUsed(true);
                    return codeRepository.save(c);
                });
    }

    /**
     * Invalidates all refresh tokens derived from the given access token JTI (replay attack mitigation).
     */
    @Transactional
    public void invalidateTokensByJti(String jti) {
        refreshTokenRepository.revokeByAccessTokenJti(jti);
    }
}
