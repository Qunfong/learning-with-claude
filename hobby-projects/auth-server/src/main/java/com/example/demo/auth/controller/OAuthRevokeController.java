package com.example.demo.auth.controller;

import com.example.demo.auth.entity.RefreshToken;
import com.example.demo.auth.repository.RefreshTokenRepository;
import com.example.demo.auth.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 6.2 Token revocation endpoint (RFC 7009).
 * Always returns 200 regardless of whether the token was found.
 */
@RestController
@RequestMapping("/oauth/revoke")
public class OAuthRevokeController {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;

    public OAuthRevokeController(RefreshTokenRepository refreshTokenRepository, TokenService tokenService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
    }

    @PostMapping(consumes = {"application/x-www-form-urlencoded", "application/json"})
    @Transactional
    public ResponseEntity<Void> revoke(@RequestParam("token") String rawToken) {
        String tokenHash = tokenService.hashToken(rawToken);
        Optional<RefreshToken> rtOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        rtOpt.ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
        // RFC 7009 §2.2: always return 200
        return ResponseEntity.ok().build();
    }
}
