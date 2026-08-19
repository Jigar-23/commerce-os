package com.commerceos.identity.repository;

import com.commerceos.identity.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    List<UserSession> findByUserIdAndIsRevokedFalse(UUID userId);
    List<UserSession> findByUserId(UUID userId);
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);
}
