package com.basketbot.service;

import com.basketbot.config.TelegramBotProperties;
import com.basketbot.model.Invitation;
import com.basketbot.model.TeamMember;
import com.basketbot.repository.InvitationRepository;
import com.basketbot.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvitationService {

    private static final int DEFAULT_EXPIRES_DAYS = 7;

    private final InvitationRepository invitationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberService teamMemberService;
    private final TelegramBotProperties botProperties;

    public InvitationService(InvitationRepository invitationRepository,
                             TeamRepository teamRepository,
                             TeamMemberService teamMemberService,
                             TelegramBotProperties botProperties) {
        this.invitationRepository = invitationRepository;
        this.teamRepository = teamRepository;
        this.teamMemberService = teamMemberService;
        this.botProperties = botProperties;
    }

    /** Ссылка для приглашения (t.me/BotUsername?start=CODE). */
    public String buildInviteLink(String code) {
        String username = botProperties.getUsername() != null ? botProperties.getUsername().replace("@", "") : "BasketBot";
        return "https://t.me/" + username + "?start=" + code;
    }

    @Transactional
    public Invitation create(Long teamId, TeamMember.Role role, int expiresInDays) {
        var team = teamRepository.getReferenceById(teamId);
        String code = generateUniqueCode();
        Instant expiresAt = Instant.now().plusSeconds(expiresInDays * 86400L);
        Invitation inv = new Invitation();
        inv.setTeam(team);
        inv.setCode(code);
        inv.setRole(role != null ? role : TeamMember.Role.PLAYER);
        inv.setExpiresAt(expiresAt);
        return invitationRepository.save(inv);
    }

    @Transactional(readOnly = true)
    public Optional<Invitation> findByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return invitationRepository.findByCode(code.trim())
                .filter(inv -> inv.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Использовать приглашение: добавить пользователя в команду с указанной ролью и удалить приглашение.
     *
     * @param telegramUsername опционально — @username из Telegram для отображения в админке
     * @return имя команды и роль при успехе
     */
    @Transactional
    public Optional<InvitationUseResult> use(String code, String telegramUserId, String telegramUsername) {
        if (telegramUserId == null || telegramUserId.isBlank()) return Optional.empty();
        Optional<Invitation> opt = invitationRepository.findByCode(code != null ? code.trim() : "");
        if (opt.isEmpty()) return Optional.empty();
        Invitation inv = opt.get();
        if (inv.getExpiresAt().isBefore(Instant.now())) return Optional.empty();
        Long teamId = inv.getTeam().getId();
        TeamMember.Role role = inv.getRole();
        teamMemberService.setRoleByAdmin(teamId, telegramUserId, role);
        if (telegramUsername != null && !telegramUsername.isBlank()) {
            teamMemberService.ensureTelegramUsername(teamId, telegramUserId, telegramUsername);
        }
        String teamName = inv.getTeam().getName();
        invitationRepository.delete(inv);
        return Optional.of(new InvitationUseResult(teamName, role));
    }

    @Transactional(readOnly = true)
    public List<Invitation> findByTeamId(Long teamId) {
        return invitationRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
    }

    @Transactional
    public boolean deleteByCode(Long teamId, String code) {
        return invitationRepository.findByCode(code != null ? code.trim() : "")
                .filter(inv -> inv.getTeam().getId().equals(teamId))
                .map(inv -> {
                    invitationRepository.delete(inv);
                    return true;
                })
                .orElse(false);
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (invitationRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate unique invitation code");
    }

    public record InvitationUseResult(String teamName, TeamMember.Role role) {}
}
