package com.example.demo.auth.service;

import com.example.demo.auth.entity.RefreshToken;
import com.example.demo.auth.model.TokenResponse;
import com.example.demo.auth.repository.RefreshTokenRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

    private final RSAKey rsaKey;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${ACCESS_TOKEN_TTL:900}")
    private long accessTokenTtl;

    @Value("${REFRESH_TOKEN_TTL:2592000}")
    private long refreshTokenTtl;

    public TokenService(RSAKey rsaKey, RefreshTokenRepository refreshTokenRepository) {
        this.rsaKey = rsaKey;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public String issueAccessToken(String userId, String clientId) throws Exception {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("client_id", clientId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtl)))
                .jwtID(jti)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(rsaKey);
        jwt.sign(signer);
        return jwt.serialize();
    }

    @Transactional
    public String issueRefreshToken(String userId, String clientId, String accessTokenJti) throws Exception {
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String tokenHash = hashToken(rawToken);

        RefreshToken rt = new RefreshToken();
        rt.setTokenHash(tokenHash);
        rt.setClientId(clientId);
        rt.setUserId(userId);
        rt.setAccessTokenJti(accessTokenJti);
        rt.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtl));
        refreshTokenRepository.save(rt);

        return rawToken;
    }

    @Transactional
    public TokenResponse issueTokenPair(String userId, String clientId) throws Exception {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim("client_id", clientId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtl)))
                .jwtID(jti)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKey.getKeyID())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(rsaKey);
        jwt.sign(signer);
        String accessToken = jwt.serialize();

        String rawRefreshToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken rt = new RefreshToken();
        rt.setTokenHash(tokenHash);
        rt.setClientId(clientId);
        rt.setUserId(userId);
        rt.setAccessTokenJti(jti);
        rt.setExpiresAt(now.plusSeconds(refreshTokenTtl));
        refreshTokenRepository.save(rt);

        return new TokenResponse(accessToken, accessTokenTtl, rawRefreshToken);
    }

    public Optional<SignedJWT> validateAccessToken(String tokenStr) {
        try {
            SignedJWT jwt = SignedJWT.parse(tokenStr);
            var verifier = new com.nimbusds.jose.crypto.RSASSAVerifier(rsaKey.toRSAPublicKey());
            if (!jwt.verify(verifier)) return Optional.empty();
            Date exp = jwt.getJWTClaimsSet().getExpirationTime();
            if (exp == null || exp.before(new Date())) return Optional.empty();
            return Optional.of(jwt);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public long getAccessTokenTtl() { return accessTokenTtl; }
}
