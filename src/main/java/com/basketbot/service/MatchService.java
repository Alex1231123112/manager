package com.basketbot.service;

import com.basketbot.model.Match;
import com.basketbot.model.Team;
import com.basketbot.repository.MatchRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    public MatchService(MatchRepository matchRepository, TeamRepository teamRepository) {
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<Match> findByTeamIdOrderByDateDesc(Long teamId) {
        return matchRepository.findByTeamIdOrderByDateDesc(teamId);
    }

    @Transactional
    public Match createMatch(Long teamId, String opponent, Instant date, String location) {
        Match match = new Match();
        Team team = teamRepository.getReferenceById(teamId);
        match.setTeam(team);
        match.setOpponent(opponent.trim());
        match.setDate(date != null ? date : Instant.now());
        match.setLocation(location != null && !location.isBlank() ? location.trim() : null);
        return matchRepository.save(match);
    }

    @Transactional(readOnly = true)
    public Optional<Match> findLastScheduled(Long teamId) {
        return matchRepository.findFirstByTeamIdAndStatusOrderByDateDesc(teamId, Match.Status.SCHEDULED);
    }

    @Transactional(readOnly = true)
    public Optional<Match> findByIdAndTeamId(Long matchId, Long teamId) {
        return matchRepository.findById(matchId)
                .filter(m -> m.getTeam().getId().equals(teamId));
    }

    @Transactional
    public Match setResult(Long matchId, Long teamId, int ourScore, int opponentScore) {
        Match match = matchRepository.findById(matchId)
                .filter(m -> m.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new IllegalArgumentException("Матч не найден"));
        match.setOurScore(ourScore);
        match.setOpponentScore(opponentScore);
        match.setStatus(Match.Status.COMPLETED);
        return matchRepository.save(match);
    }

    @Transactional(readOnly = true)
    public Optional<Match> findNextScheduled(Long teamId) {
        return matchRepository.findFirstByTeamIdAndStatusAndDateAfterOrderByDateAsc(
                teamId, Match.Status.SCHEDULED, Instant.now());
    }

    @Transactional
    public Match updateMatch(Long teamId, Long matchId, String opponent, Instant date, String location) {
        Match match = matchRepository.findById(matchId)
                .filter(m -> m.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new IllegalArgumentException("Матч не найден"));
        if (opponent != null && !opponent.isBlank()) match.setOpponent(opponent.trim());
        if (date != null) match.setDate(date);
        if (location != null) match.setLocation(location.isBlank() ? null : location.trim());
        return matchRepository.save(match);
    }

    @Transactional
    public void cancelMatch(Long teamId, Long matchId) {
        Match match = matchRepository.findById(matchId)
                .filter(m -> m.getTeam().getId().equals(teamId))
                .orElseThrow(() -> new IllegalArgumentException("Матч не найден"));
        match.setStatus(Match.Status.CANCELLED);
        matchRepository.save(match);
    }
}
