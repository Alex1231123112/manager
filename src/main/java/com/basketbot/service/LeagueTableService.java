package com.basketbot.service;

import com.basketbot.model.LeagueTableRow;
import com.basketbot.model.Team;
import com.basketbot.repository.LeagueTableRowRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LeagueTableService {

    private final LeagueTableRowRepository repository;
    private final TeamRepository teamRepository;

    public LeagueTableService(LeagueTableRowRepository repository, TeamRepository teamRepository) {
        this.repository = repository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<LeagueTableRow> findByTeamId(Long teamId) {
        return repository.findByTeamIdOrderByPositionAsc(teamId);
    }

    @Transactional(readOnly = true)
    public Optional<LeagueTableRow> findByIdAndTeamId(Long id, Long teamId) {
        return repository.findById(id)
                .filter(r -> r.getTeam().getId().equals(teamId));
    }

    @Transactional
    public LeagueTableRow create(Long teamId, int position, String teamName, int wins, int losses, int pointsDiff) {
        Team team = teamRepository.getReferenceById(teamId);
        LeagueTableRow row = new LeagueTableRow();
        row.setTeam(team);
        row.setPosition(position);
        row.setTeamName(teamName != null ? teamName.trim() : "");
        row.setWins(wins);
        row.setLosses(losses);
        row.setPointsDiff(pointsDiff);
        return repository.save(row);
    }

    @Transactional
    public void deleteByIdAndTeamId(Long id, Long teamId) {
        findByIdAndTeamId(id, teamId).ifPresent(repository::delete);
    }
}
