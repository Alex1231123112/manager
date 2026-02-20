package com.basketbot.repository;

import com.basketbot.model.MatchPlayerStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchPlayerStatRepository extends JpaRepository<MatchPlayerStat, Long> {

    List<MatchPlayerStat> findByMatchId(Long matchId);

    Optional<MatchPlayerStat> findByMatchIdAndPlayerId(Long matchId, Long playerId);
}
