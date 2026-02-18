package com.basketbot.repository;

import com.basketbot.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByTeamId(Long teamId);

    List<Player> findByTeamIdOrderByNameAsc(Long teamId);

    List<Player> findByTeamIdAndActiveTrue(Long teamId);

    List<Player> findByTeamIdAndPlayerStatus(Long teamId, Player.PlayerStatus status);

    List<Player> findByTeamIdAndDebtGreaterThan(Long teamId, BigDecimal minDebt);

    java.util.Optional<Player> findByTeamIdAndTelegramId(Long teamId, String telegramId);
}
