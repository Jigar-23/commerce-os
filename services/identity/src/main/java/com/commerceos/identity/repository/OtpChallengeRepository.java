package com.commerceos.identity.repository;

import com.commerceos.identity.domain.OtpChallengeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallengeEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OtpChallengeEntity o WHERE o.challengeId = :challengeId")
    Optional<OtpChallengeEntity> findByChallengeIdForUpdate(@Param("challengeId") String challengeId);

    Optional<OtpChallengeEntity> findByChallengeId(String challengeId);

    @Query("SELECT COUNT(o) FROM OtpChallengeEntity o WHERE o.phoneHash = :phoneHash AND o.createdAt >= :since")
    long countRecentChallengesForPhone(@Param("phoneHash") String phoneHash, @Param("since") Instant since);

    @Query("SELECT COUNT(o) FROM OtpChallengeEntity o WHERE o.ipAddress = :ipAddress AND o.createdAt >= :since")
    long countRecentChallengesForIp(@Param("ipAddress") String ipAddress, @Param("since") Instant since);
}
