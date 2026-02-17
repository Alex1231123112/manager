package com.basketbot.service;

import com.basketbot.model.Player;
import com.basketbot.model.Team;
import com.basketbot.repository.PlayerRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    public PlayerService(PlayerRepository playerRepository, TeamRepository teamRepository) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<Player> findByTeamId(Long teamId) {
        return playerRepository.findByTeamIdOrderByNameAsc(teamId);
    }

    @Transactional(readOnly = true)
    public List<Player> findByTeamIdByStatus(Long teamId, Player.PlayerStatus status) {
        if (status == null) {
            return playerRepository.findByTeamIdOrderByNameAsc(teamId);
        }
        return playerRepository.findByTeamIdAndPlayerStatus(teamId, status);
    }

    @Transactional
    public Player addPlayer(Long teamId, String name, Integer number) {
        String trimmedName = name != null ? name.trim() : "";
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Имя игрока не может быть пустым.");
        }
        if (number != null && number < 0) {
            throw new IllegalArgumentException("Номер не может быть отрицательным.");
        }
        Player player = new Player();
        Team team = teamRepository.getReferenceById(teamId);
        player.setTeam(team);
        player.setName(trimmedName);
        player.setNumber(number);
        return playerRepository.save(player);
    }

    @Transactional(readOnly = true)
    public List<Player> findWithDebt(Long teamId) {
        return playerRepository.findByTeamIdAndDebtGreaterThan(teamId, BigDecimal.ZERO);
    }

    @Transactional
    public Player setDebt(Long teamId, String playerName, BigDecimal amount) {
        String search = playerName != null ? playerName.trim() : "";
        if (search.isEmpty()) {
            throw new IllegalArgumentException("Укажи имя игрока.");
        }
        List<Player> all = playerRepository.findByTeamId(teamId);
        Player player = all.stream()
                .filter(p -> p.getName().equalsIgnoreCase(search) || p.getName().toLowerCase().contains(search.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Игрок не найден: " + search));
        player.setDebt(amount != null ? amount : BigDecimal.ZERO);
        return playerRepository.save(player);
    }

    /** Отметить оплату: сброс долга по имени игрока. */
    @Transactional
    public Player clearDebt(Long teamId, String playerName) {
        return setDebt(teamId, playerName, BigDecimal.ZERO);
    }

    /** Отметить оплату: сброс долга по id игрока (с проверкой team_id). */
    @Transactional
    public Player clearDebtByPlayerId(Long teamId, Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Игрок не найден."));
        if (!player.getTeam().getId().equals(teamId)) {
            throw new IllegalArgumentException("Игрок не из этой команды.");
        }
        player.setDebt(BigDecimal.ZERO);
        return playerRepository.save(player);
    }

    @Transactional
    public Player setPlayerStatus(Long teamId, String playerName, Player.PlayerStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Укажи статус: активен, травма, отпуск, не оплатил");
        }
        String search = playerName != null ? playerName.trim() : "";
        if (search.isEmpty()) {
            throw new IllegalArgumentException("Укажи имя игрока.");
        }
        List<Player> all = playerRepository.findByTeamId(teamId);
        Player player = all.stream()
                .filter(p -> p.getName().equalsIgnoreCase(search) || p.getName().toLowerCase().contains(search.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Игрок не найден: " + search));
        player.setPlayerStatus(status);
        return playerRepository.save(player);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Player> findByIdAndTeamId(Long playerId, Long teamId) {
        return playerRepository.findById(playerId)
                .filter(p -> p.getTeam().getId().equals(teamId));
    }

    @Transactional
    public Player updatePlayer(Long teamId, Long playerId, String name, Integer number, Player.PlayerStatus status) {
        Player player = findByIdAndTeamId(playerId, teamId)
                .orElseThrow(() -> new IllegalArgumentException("Игрок не найден."));
        if (name != null && !name.trim().isEmpty()) player.setName(name.trim());
        if (number != null) player.setNumber(number);
        if (status != null) player.setPlayerStatus(status);
        return playerRepository.save(player);
    }

    @Transactional
    public void deletePlayer(Long teamId, Long playerId) {
        Player player = findByIdAndTeamId(playerId, teamId)
                .orElseThrow(() -> new IllegalArgumentException("Игрок не найден."));
        playerRepository.delete(player);
    }
}
