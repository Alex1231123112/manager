package com.basketbot.service;

import com.basketbot.model.Team;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TeamService {

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<Team> findAll() {
        return teamRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Team> findById(Long id) {
        return teamRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Team> findByTelegramChatId(String telegramChatId) {
        return teamRepository.findByTelegramChatId(telegramChatId);
    }

    @Transactional
    public Team createTeam(String name, String telegramChatId) {
        Team team = new Team();
        team.setName(name.trim());
        team.setTelegramChatId(telegramChatId);
        return teamRepository.save(team);
    }

    @Transactional
    public Team setChannelChatId(Long teamId, String channelTelegramChatId) {
        Team team = teamRepository.getReferenceById(teamId);
        team.setChannelTelegramChatId(channelTelegramChatId != null && !channelTelegramChatId.isBlank() ? channelTelegramChatId.trim() : null);
        return teamRepository.save(team);
    }

    @Transactional
    public Team setGroupChatId(Long teamId, String groupTelegramChatId) {
        Team team = teamRepository.getReferenceById(teamId);
        team.setGroupTelegramChatId(groupTelegramChatId != null && !groupTelegramChatId.isBlank() ? groupTelegramChatId.trim() : null);
        return teamRepository.save(team);
    }
}
