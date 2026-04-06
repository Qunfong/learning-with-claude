package com.example.demo.auth.repository;

import com.example.demo.auth.entity.OAuthClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OAuthClientRepository extends JpaRepository<OAuthClient, Long> {

    Optional<OAuthClient> findByClientId(String clientId);

    @Modifying
    @Query("UPDATE OAuthClient c SET c.active = false WHERE c.clientId = :clientId")
    int deactivateByClientId(String clientId);
}
