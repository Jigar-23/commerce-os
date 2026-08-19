package com.commerceos.identity.repository;

import com.commerceos.identity.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByTokenCode(String tokenCode);
    Optional<VerificationToken> findByUserIdAndTokenTypeAndIsUsedFalse(UUID userId, String tokenType);
}
