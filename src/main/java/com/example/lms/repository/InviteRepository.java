package com.example.lms.repository;

import com.example.lms.entity.InviteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<InviteEntity, Long> {

    Optional<InviteEntity> findByToken(String token);

    boolean existsByEmailIgnoreCaseAndStatus(String email, String status);

    // NEW - pending invites of one role (shown as "Invited" in Role Management).
    List<InviteEntity> findByRoleIdAndInvitedByAdminIdAndStatus(Long roleId, Long adminId, String status);
}