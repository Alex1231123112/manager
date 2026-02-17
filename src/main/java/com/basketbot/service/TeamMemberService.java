package com.basketbot.service;

import com.basketbot.model.Team;
import com.basketbot.model.TeamMember;
import com.basketbot.repository.TeamMemberRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;

    public TeamMemberService(TeamMemberRepository teamMemberRepository, TeamRepository teamRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
    }

    /** Добавить участника как админа (создатель команды). */
    @Transactional
    public TeamMember addAsAdmin(Long teamId, String telegramUserId) {
        Team team = teamRepository.getReferenceById(teamId);
        return teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId)
                .map(existing -> {
                    existing.setRole(TeamMember.Role.ADMIN);
                    return teamMemberRepository.save(existing);
                })
                .orElseGet(() -> {
                    TeamMember m = new TeamMember();
                    m.setTeam(team);
                    m.setTelegramUserId(telegramUserId);
                    m.setRole(TeamMember.Role.ADMIN);
                    return teamMemberRepository.save(m);
                });
    }

    /** Роль участника в команде. Пусто = не в списке (считать как нет прав на опасные действия).
     * Для команд без записей в team_members (legacy) считаем любого пользователя админом. */
    @Transactional(readOnly = true)
    public Optional<TeamMember.Role> getRole(Long teamId, String telegramUserId) {
        if (teamMemberRepository.findByTeamId(teamId).isEmpty()) {
            return Optional.of(TeamMember.Role.ADMIN);
        }
        return teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId)
                .map(TeamMember::getRole);
    }

    /** Требуется минимум указанная роль (ADMIN >= CAPTAIN >= PLAYER). Иначе исключение. */
    @Transactional(readOnly = true)
    public void requireAtLeast(Long teamId, String telegramUserId, TeamMember.Role minRole) {
        TeamMember.Role current = getRole(teamId, telegramUserId).orElse(TeamMember.Role.PLAYER);
        if (TeamMember.roleLevel(current) < TeamMember.roleLevel(minRole)) {
            throw new SecurityException("Только админ или капитан может выполнить это действие.");
        }
    }

    /** Назначить роль участнику. Может только админ (в боте). */
    @Transactional
    public TeamMember setRole(Long teamId, String callerTelegramUserId, String targetTelegramUserId, TeamMember.Role role) {
        requireAtLeast(teamId, callerTelegramUserId, TeamMember.Role.ADMIN);
        return setRoleInternal(teamId, targetTelegramUserId, role);
    }

    /** Назначить роль участнику из веб-админки (доступ только у авторизованного админа панели). */
    @Transactional
    public TeamMember setRoleByAdmin(Long teamId, String targetTelegramUserId, TeamMember.Role role) {
        if (role == null) {
            throw new IllegalArgumentException("Укажи роль: ADMIN, CAPTAIN, PLAYER");
        }
        return setRoleInternal(teamId, targetTelegramUserId, role);
    }

    @Transactional(readOnly = true)
    public List<TeamMember> findByTeamId(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId);
    }

    /** Обновить отображаемое имя участника (из админки). */
    @Transactional
    public void updateDisplayName(Long teamId, String telegramUserId, String displayName) {
        teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId).ifPresent(m -> {
            m.setDisplayName(displayName != null && !displayName.isBlank() ? displayName.trim() : null);
            teamMemberRepository.save(m);
        });
    }

    /** Обновить @username участника (вызывается из бота при взаимодействии пользователя). */
    @Transactional
    public void ensureTelegramUsername(Long teamId, String telegramUserId, String telegramUsername) {
        if (telegramUserId == null || telegramUserId.isBlank()) return;
        String username = telegramUsername != null ? telegramUsername.trim().replaceFirst("^@", "") : null;
        if (username == null || username.isBlank()) return;
        teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId).ifPresent(m -> {
            m.setTelegramUsername(username);
            teamMemberRepository.save(m);
        });
    }

    private TeamMember setRoleInternal(Long teamId, String targetTelegramUserId, TeamMember.Role role) {
        Team team = teamRepository.getReferenceById(teamId);
        TeamMember member = teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, targetTelegramUserId)
                .orElseGet(() -> {
                    TeamMember m = new TeamMember();
                    m.setTeam(team);
                    m.setTelegramUserId(targetTelegramUserId);
                    return m;
                });
        member.setRole(role);
        return teamMemberRepository.save(member);
    }
}
