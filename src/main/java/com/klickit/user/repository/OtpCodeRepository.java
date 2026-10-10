package com.klickit.user.repository;

import com.klickit.user.entity.OtpCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, Instant after);

    /** Row-locks the active code so two concurrent verifications cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpCode> findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(String email);

    @Modifying
    @Transactional
    @Query("update OtpCode o set o.consumedAt = :now where o.email = :email and o.consumedAt is null")
    int invalidateActive(@Param("email") String email, @Param("now") Instant now);

    @Modifying
    @Transactional
    long deleteByExpiresAtBefore(Instant cutoff);

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext(:email))", nativeQuery = true)
    Integer acquireEmailLock(@Param("email") String email);
}
