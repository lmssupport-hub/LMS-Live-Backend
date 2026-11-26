package com.example.lms.repository;

import com.example.lms.entity.PersonalDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PersonalDetailsRepository extends JpaRepository<PersonalDetails, Long> {
    Optional<PersonalDetails> findByUserId(Long userId);

    /** One query for a whole table page. */
    List<PersonalDetails> findByUserIdIn(Collection<Long> userIds);
}