package com.basketbot.repository;

import com.basketbot.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByCode(String code);

    List<Invitation> findByTeamIdOrderByCreatedAtDesc(Long teamId);
}
