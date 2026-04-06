package com.example.demo.auth.repository;

import com.example.demo.auth.entity.AuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, Long> {

    Optional<AuthorizationCode> findByCode(String code);

    @Modifying
    @Query("DELETE FROM AuthorizationCode a WHERE a.used = true OR a.expiresAt < :now")
    int purgeExpiredAndUsed(Instant now);
}
