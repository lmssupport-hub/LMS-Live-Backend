package com.example.lms.repository;

import com.example.lms.entity.TwoFactorChallenge;
import com.example.lms.entity.TwoFactorChallenge.Purpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, Long> {

    // PESSIMISTIC_WRITE: two parallel "verify" calls with the same correct OTP must not both succeed.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TwoFactorChallenge c where c.tokenHash = :tokenHash")
    Optional<TwoFactorChallenge> findByTokenHashForUpdate(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TwoFactorChallenge c where c.userId = :userId and c.purpose = :purpose")
    Optional<TwoFactorChallenge> findByUserIdAndPurposeForUpdate(Long userId, Purpose purpose);

    @Modifying
    @Query("delete from TwoFactorChallenge c where c.expiresAt < :cutoff")
    int deleteExpired(LocalDateTime cutoff);
}
