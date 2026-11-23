package com.example.lms.repository;

import com.example.lms.entity.StudyTimeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StudyTimeRepository extends JpaRepository<StudyTimeEntity, Long> {

    Optional<StudyTimeEntity> findByUserIdAndCourseId(Long userId, Long courseId);

    /** Atomic add (two tabs pinging at once cannot overwrite each other). */
    @Transactional
    @Modifying
    @Query("update StudyTimeEntity s set s.seconds = s.seconds + :delta, s.lastPingAt = :now where s.id = :id")
    int addSeconds(@Param("id") Long id, @Param("delta") long delta, @Param("now") LocalDateTime now);
}