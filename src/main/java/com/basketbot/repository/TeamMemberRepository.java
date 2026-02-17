package com.basketbot.repository;

import com.basketbot.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    Optional<TeamMember> findByTeamIdAndTelegramUserId(Long teamId, String telegramUserId);

    List<TeamMember> findByTeamId(Long teamId);
}
