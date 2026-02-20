package com.basketbot.service;

import com.basketbot.model.Match;
import com.basketbot.model.MatchPlayerStat;
import com.basketbot.model.Player;
import com.basketbot.repository.MatchPlayerStatRepository;
import com.basketbot.repository.MatchRepository;
import com.basketbot.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MatchPlayerStatService {

    private final MatchPlayerStatRepository statRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;

    public MatchPlayerStatService(MatchPlayerStatRepository statRepository,
                                  MatchRepository matchRepository,
                                  PlayerRepository playerRepository) {
        this.statRepository = statRepository;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public List<MatchPlayerStat> findByMatchId(Long matchId) {
        return statRepository.findByMatchId(matchId);
    }

    @Transactional(readOnly = true)
    public Optional<Player> findMvpForMatch(Long matchId) {
        return statRepository.findByMatchId(matchId).stream()
                .filter(MatchPlayerStat::isMvp)
                .findFirst()
                .map(MatchPlayerStat::getPlayer);
    }

    @Transactional
    public void saveStats(Long matchId, Long teamId, List<StatEntry> entries) {
        Match match = matchRepository.findById(matchId)
                .filter(m -> m.getTeam().getId().equals(teamId))
                .orElse(null);
        if (match == null) return;
        for (StatEntry e : entries) {
            if (e.playerId() == null) continue;
            Player player = playerRepository.findById(e.playerId())
                    .filter(p -> p.getTeam().getId().equals(teamId))
                    .orElse(null);
            if (player == null) continue;
            MatchPlayerStat stat = statRepository.findByMatchIdAndPlayerId(matchId, e.playerId())
                    .orElseGet(() -> {
                        MatchPlayerStat s = new MatchPlayerStat();
                        s.setMatch(match);
                        s.setPlayer(player);
                        return s;
                    });
            stat.setMinutes(e.minutes());
            stat.setPoints(e.points() != null ? e.points() : 0);
            stat.setRebounds(e.rebounds() != null ? e.rebounds() : 0);
            stat.setAssists(e.assists() != null ? e.assists() : 0);
            stat.setFouls(e.fouls() != null ? e.fouls() : 0);
            stat.setPlusMinus(e.plusMinus());
            stat.setMvp(Boolean.TRUE.equals(e.mvp()));
            statRepository.save(stat);
        }
    }

    /** Средние показатели игрока по всем завершённым матчам команды в которых он участвовал. */
    @Transactional(readOnly = true)
    public Averages getSeasonAverages(Long teamId, Long playerId) {
        List<Match> completed = matchRepository.findByTeamId(teamId).stream()
                .filter(m -> m.getStatus() == Match.Status.COMPLETED)
                .collect(Collectors.toList());
        List<MatchPlayerStat> playerStats = new ArrayList<>();
        for (Match m : completed) {
            statRepository.findByMatchIdAndPlayerId(m.getId(), playerId).ifPresent(playerStats::add);
        }
        if (playerStats.isEmpty()) {
            return new Averages(0, 0, 0, 0, 0);
        }
        int games = playerStats.size();
        int points = playerStats.stream().mapToInt(MatchPlayerStat::getPoints).sum();
        int rebounds = playerStats.stream().mapToInt(MatchPlayerStat::getRebounds).sum();
        int assists = playerStats.stream().mapToInt(MatchPlayerStat::getAssists).sum();
        int minutes = playerStats.stream()
                .mapToInt(s -> s.getMinutes() != null ? s.getMinutes() : 0)
                .sum();
        return new Averages(
                games,
                games > 0 ? (double) points / games : 0,
                games > 0 ? (double) rebounds / games : 0,
                games > 0 ? (double) assists / games : 0,
                games > 0 ? (double) minutes / games : 0
        );
    }

    public record StatEntry(Long playerId, Integer minutes, Integer points, Integer rebounds, Integer assists, Integer fouls, Integer plusMinus, Boolean mvp) {}
    public record Averages(int games, double pointsAvg, double reboundsAvg, double assistsAvg, double minutesAvg) {}
}
