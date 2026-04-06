package com.example.demo.auth.service;

import com.example.demo.auth.repository.AuthorizationCodeRepository;
import com.example.demo.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final AuthorizationCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenCleanupService(AuthorizationCodeRepository codeRepository,
                               RefreshTokenRepository refreshTokenRepository) {
        this.codeRepository = codeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(fixedDelay = 3_600_000) // every hour
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        int codes = codeRepository.purgeExpiredAndUsed(now);
        int tokens = refreshTokenRepository.purgeExpired(now);
        log.debug("Token cleanup: removed {} authorization codes and {} refresh tokens", codes, tokens);
    }
}
