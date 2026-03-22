package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndRevokedFalseOrderByLastSeenAtDesc(UUID userId);

    List<UserSession> findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtDesc(UUID userId, Instant now);

    Optional<UserSession> findByJti(String jti);

    Optional<UserSession> findByJtiAndRevokedFalse(String jti);

    Optional<UserSession> findByJtiAndUserId(String jti, UUID userId);

    @Modifying
    @Query("update UserSession s set s.revoked = true, s.revokedAt = :now, s.revokedReason = :reason where s.user.id = :userId and s.revoked = false")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("update UserSession s set s.revoked = true, s.revokedAt = :now, s.revokedReason = :reason where s.jti = :jti and s.user.id = :userId and s.revoked = false")
    int revokeByJtiForUser(@Param("userId") UUID userId, @Param("jti") String jti, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("""
            update UserSession s
               set s.revoked = true, s.revokedAt = :now, s.revokedReason = :reason
            where s.user.id = :userId
               and s.revoked = false
               and s.expiresAt > :now
               and ((:ip is null and s.ipAddress is null) or s.ipAddress = :ip)
               and ((:userAgent is null and s.userAgent is null) or s.userAgent = :userAgent)
            """)
    int revokeActiveForUserByFingerprint(@Param("userId") UUID userId,
                                         @Param("ip") String ip,
                                         @Param("userAgent") String userAgent,
                                         @Param("now") Instant now,
                                         @Param("reason") String reason);

    @Query("""
            select s
              from UserSession s
             where s.user.id = :userId
               and s.revoked = false
               and s.expiresAt > :now
               and ((:ip is null and s.ipAddress is null) or s.ipAddress = :ip)
               and ((:userAgent is null and s.userAgent is null) or s.userAgent = :userAgent)
             order by s.lastSeenAt desc
            """)
    List<UserSession> findActiveForUserByFingerprint(@Param("userId") UUID userId,
                                                     @Param("ip") String ip,
                                                     @Param("userAgent") String userAgent,
                                                     @Param("now") Instant now);

    @Modifying
    @Query("update UserSession s set s.lastSeenAt = :seenAt where s.jti = :jti")
    int touch(@Param("jti") String jti, @Param("seenAt") Instant seenAt);
}
