package com.example.lms.repository;

import com.example.lms.entity.LoginEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginEventRepository extends JpaRepository<LoginEventEntity, Long> {

    /** Login timestamps of one organization in [from, to) - bucketed per day / month in the service. */
    @Query("select e.loggedInAt from LoginEventEntity e " +
           "where e.organizationId = :orgId and e.loggedInAt >= :from and e.loggedInAt < :to")
    List<LocalDateTime> findTimes(@Param("orgId") Long orgId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);
}