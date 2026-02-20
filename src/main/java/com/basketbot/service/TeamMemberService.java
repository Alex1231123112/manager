package com.basketbot.service;

import com.basketbot.model.Player;
import com.basketbot.model.Team;
import com.basketbot.model.TeamMember;
import com.basketbot.repository.TeamMemberRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class TeamMemberService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final PlayerService playerService;

    public TeamMemberService(TeamMemberRepository teamMemberRepository, TeamRepository teamRepository,
                             PlayerService playerService) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.playerService = playerService;
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

    /** Роль участника в команде. Пусто = не в списке или деактивирован (считать как нет прав).
     * Для команд без записей в team_members (legacy) считаем любого пользователя админом. */
    @Transactional(readOnly = true)
    public Optional<TeamMember.Role> getRole(Long teamId, String telegramUserId) {
        if (teamMemberRepository.findByTeamId(teamId).isEmpty()) {
            return Optional.of(TeamMember.Role.ADMIN);
        }
        return teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId)
                .filter(TeamMember::isActive)
                .map(TeamMember::getRole);
    }

    /** Требуется минимум указанная роль (ADMIN >= PLAYER). Иначе исключение. */
    @Transactional(readOnly = true)
    public void requireAtLeast(Long teamId, String telegramUserId, TeamMember.Role minRole) {
        TeamMember.Role current = getRole(teamId, telegramUserId).orElse(TeamMember.Role.PLAYER);
        if (TeamMember.roleLevel(current) < TeamMember.roleLevel(minRole)) {
            throw new SecurityException("Только админ может выполнить это действие.");
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
            throw new IllegalArgumentException("Укажи роль: ADMIN или PLAYER");
        }
        return setRoleInternal(teamId, targetTelegramUserId, role);
    }

    @Transactional(readOnly = true)
    public List<TeamMember> findByTeamId(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId);
    }

    /** Первая команда пользователя (по участию), активный участник. Нужно для бота: опрос из лички. */
    @Transactional(readOnly = true)
    public Optional<Team> findFirstTeamByTelegramUserId(String telegramUserId) {
        if (telegramUserId == null || telegramUserId.isBlank()) return Optional.empty();
        return teamMemberRepository.findByTelegramUserId(telegramUserId).stream()
                .filter(TeamMember::isActive)
                .findFirst()
                .map(TeamMember::getTeam);
    }

    /** Может ли участник пользоваться ботом: активен в команде. Деактивированные в админке — нет. */
    @Transactional(readOnly = true)
    public boolean canUseBot(Long teamId, String telegramUserId) {
        if (teamId == null || telegramUserId == null || telegramUserId.isBlank()) return false;
        if (teamMemberRepository.findByTeamId(teamId).isEmpty()) return true; // legacy: команда без записей
        return teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId)
                .filter(TeamMember::isActive)
                .isPresent();
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

    /** Полное обновление участника из админки: имя, роль, активность, номер, статус, долг. Синхронизирует с записью игрока (Player). */
    @Transactional
    public void updateMemberFull(Long teamId, String telegramUserId, String displayName, TeamMember.Role role,
                                  Boolean isActive, Integer number, Player.PlayerStatus status, BigDecimal debt) {
        teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId).ifPresent(m -> {
            if (displayName != null) m.setDisplayName(displayName.isBlank() ? null : displayName.trim());
            if (role != null) m.setRole(role);
            if (isActive != null) m.setActive(isActive);
            teamMemberRepository.save(m);
            String name = m.getDisplayName() != null ? m.getDisplayName() : ("ID " + m.getTelegramUserId());
            playerService.upsertPlayerForMember(teamId, telegramUserId, name, number, status, debt, isActive != null ? isActive : m.isActive());
        });
    }

    /** Выйти из команды: деактивировать себя (is_active = false). */
    @Transactional
    public void leaveTeam(Long teamId, String telegramUserId) {
        teamMemberRepository.findByTeamIdAndTelegramUserId(teamId, telegramUserId).ifPresent(m -> {
            m.setActive(false);
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
