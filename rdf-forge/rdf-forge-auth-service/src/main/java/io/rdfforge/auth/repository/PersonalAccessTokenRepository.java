package io.rdfforge.auth.repository;

import io.rdfforge.auth.entity.PersonalAccessTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessTokenEntity, UUID> {

    List<PersonalAccessTokenEntity> findByUserIdAndRevokedFalseOrderByCreatedAtDesc(UUID userId);

    List<PersonalAccessTokenEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PersonalAccessTokenEntity> findByTokenHash(String tokenHash);

    Optional<PersonalAccessTokenEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE PersonalAccessTokenEntity t SET t.lastUsedAt = :timestamp, t.lastUsedIp = :ip WHERE t.id = :id")
    void updateLastUsed(@Param("id") UUID id, @Param("timestamp") Instant timestamp, @Param("ip") String ip);

    @Modifying
    @Query("UPDATE PersonalAccessTokenEntity t SET t.revoked = true, t.revokedAt = :timestamp WHERE t.id = :id")
    void revokeToken(@Param("id") UUID id, @Param("timestamp") Instant timestamp);

    @Modifying
    @Query("UPDATE PersonalAccessTokenEntity t SET t.revoked = true, t.revokedAt = :timestamp WHERE t.userId = :userId AND t.revoked = false")
    void revokeAllUserTokens(@Param("userId") UUID userId, @Param("timestamp") Instant timestamp);

    @Query("SELECT COUNT(t) FROM PersonalAccessTokenEntity t WHERE t.userId = :userId AND t.revoked = false")
    long countActiveTokensByUserId(@Param("userId") UUID userId);

    boolean existsByTokenHash(String tokenHash);
}
