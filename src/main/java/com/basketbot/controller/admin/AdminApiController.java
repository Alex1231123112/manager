package com.basketbot.controller.admin;

import com.basketbot.model.Event;
import com.basketbot.model.FinanceEntry;
import com.basketbot.model.Match;
import com.basketbot.model.LeagueTableRow;
import com.basketbot.model.MatchPlayerStat;
import com.basketbot.model.Player;
import com.basketbot.model.Team;
import com.basketbot.model.Invitation;
import com.basketbot.model.TeamMember;
import com.basketbot.service.EventService;
import com.basketbot.service.FinanceEntryService;
import com.basketbot.service.LeagueTableService;
import com.basketbot.service.MatchPlayerStatService;
import com.basketbot.service.InvitationService;
import com.basketbot.service.MatchImageService;
import com.basketbot.service.TeamMemberService;
import com.basketbot.service.MatchPostService;
import com.basketbot.service.MatchService;
import com.basketbot.service.PlayerService;
import com.basketbot.service.SystemSettingsService;
import com.basketbot.service.TeamService;
import jakarta.servlet.http.HttpSession;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private static final Logger log = LoggerFactory.getLogger(AdminApiController.class);
    private static final String SESSION_TEAM_ID = "adminTeamId";

    private final TeamService teamService;
    private final PlayerService playerService;
    private final MatchService matchService;
    private final MatchPostService matchPostService;
    private final MatchImageService matchImageService;
    private final TelegramClient telegramClient;
    private final TeamMemberService teamMemberService;
    private final SystemSettingsService systemSettingsService;
    private final InvitationService invitationService;
    private final FinanceEntryService financeEntryService;
    private final EventService eventService;
    private final MatchPlayerStatService matchPlayerStatService;
    private final LeagueTableService leagueTableService;
    private final AuthenticationConfiguration authConfig;

    public AdminApiController(TeamService teamService, PlayerService playerService,
                             MatchService matchService, MatchPostService matchPostService,
                             MatchImageService matchImageService, TelegramClient telegramClient,
                             TeamMemberService teamMemberService, SystemSettingsService systemSettingsService,
                             InvitationService invitationService, FinanceEntryService financeEntryService,
                             EventService eventService, MatchPlayerStatService matchPlayerStatService,
                             LeagueTableService leagueTableService, AuthenticationConfiguration authConfig) {
        this.teamService = teamService;
        this.playerService = playerService;
        this.matchService = matchService;
        this.matchPostService = matchPostService;
        this.matchImageService = matchImageService;
        this.telegramClient = telegramClient;
        this.teamMemberService = teamMemberService;
        this.systemSettingsService = systemSettingsService;
        this.invitationService = invitationService;
        this.financeEntryService = financeEntryService;
        this.eventService = eventService;
        this.matchPlayerStatService = matchPlayerStatService;
        this.leagueTableService = leagueTableService;
        this.authConfig = authConfig;
    }

    private Long requireTeamId(HttpSession session) {
        return (Long) session.getAttribute(SESSION_TEAM_ID);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest body, jakarta.servlet.http.HttpServletRequest request) {
        try {
            Authentication auth = authConfig.getAuthenticationManager().authenticate(
                    new UsernamePasswordAuthenticationToken(body.username(), body.password()));
            SecurityContextHolder.getContext().setAuthentication(auth);
            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
            return ResponseEntity.ok(new LoginResponse(true, null));
        } catch (Exception e) {
            log.info("Login failed for user: {} ({})", body.username() != null ? body.username() : "(null)", e.getClass().getSimpleName());
            return ResponseEntity.status(401).body(new LoginResponse(false, "Неверный логин или пароль"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(jakarta.servlet.http.HttpServletRequest request) {
        request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(HttpSession session) {
        Long teamId = (Long) session.getAttribute(SESSION_TEAM_ID);
        List<TeamDto> teams = teamService.findAll().stream().map(this::toTeamDto).collect(Collectors.toList());
        TeamDto currentTeam = teamId != null ? teamService.findById(teamId).map(this::toTeamDto).orElse(null) : null;
        return ResponseEntity.ok(new MeResponse(teams, currentTeam));
    }

    @PostMapping("/team-select")
    public ResponseEntity<TeamSelectResponse> teamSelect(@RequestBody TeamSelectRequest body, HttpSession session) {
        if (body.teamId() == null || teamService.findById(body.teamId()).isEmpty()) {
            return ResponseEntity.badRequest().body(new TeamSelectResponse(false, "Команда не найдена"));
        }
        session.setAttribute(SESSION_TEAM_ID, body.teamId());
        return ResponseEntity.ok(new TeamSelectResponse(true, null));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> dashboard(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        Team team = teamService.findById(teamId).orElse(null);
        if (team == null) return ResponseEntity.status(403).build();
        List<Player> players = playerService.findByTeamId(teamId);
        List<Player> debtors = playerService.findWithDebt(teamId);
        BigDecimal totalDebt = debtors.stream().map(Player::getDebt).reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<Match> nextMatch = matchService.findNextScheduled(teamId);
        return ResponseEntity.ok(new DashboardDto(
                toTeamDto(team),
                players.size(),
                debtors.size(),
                totalDebt,
                nextMatch.map(this::toMatchDto).orElse(null)
        ));
    }

    @GetMapping("/members")
    public ResponseEntity<List<MemberDto>> members(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        List<MemberDto> list = teamMemberService.findByTeamId(teamId).stream()
                .map(m -> {
                    Optional<Player> playerOpt = playerService.findByTeamIdAndTelegramId(teamId, m.getTelegramUserId());
                    Integer number = playerOpt.map(Player::getNumber).orElse(null);
                    String status = playerOpt.map(p -> p.getPlayerStatus().name()).orElse(Player.PlayerStatus.ACTIVE.name());
                    BigDecimal debt = playerOpt.map(Player::getDebt).orElse(BigDecimal.ZERO);
                    return new MemberDto(
                            m.getTelegramUserId(),
                            m.getTelegramUsername() != null ? m.getTelegramUsername() : "",
                            m.getDisplayName() != null ? m.getDisplayName() : "",
                            m.getRole().name(),
                            number,
                            status,
                            debt != null ? debt : BigDecimal.ZERO,
                            m.isActive());
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/members/{telegramUserId}")
    public ResponseEntity<ActionResult> updateMember(@PathVariable String telegramUserId,
                                                      @RequestBody MemberUpdateRequest body,
                                                      HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        if (telegramUserId == null || telegramUserId.isBlank()) return ResponseEntity.badRequest().body(new ActionResult(false, null, "Укажи участника"));
        TeamMember.Role role = null;
        if (body != null && body.role() != null && !body.role().isBlank()) {
            try {
                role = TeamMember.Role.valueOf(body.role().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ActionResult(false, null, "Роль: ADMIN или PLAYER"));
            }
        }
        Player.PlayerStatus status = null;
        if (body != null && body.status() != null && !body.status().isBlank()) {
            try {
                status = Player.PlayerStatus.valueOf(body.status().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new ActionResult(false, null, "Статус: ACTIVE, INJURY, VACATION, NOT_PAID"));
            }
        }
        teamMemberService.updateMemberFull(teamId, telegramUserId.trim(),
                body != null ? body.displayName() : null,
                role,
                body != null ? body.isActive() : null,
                body != null ? body.number() : null,
                status,
                body != null && body.debt() != null ? body.debt() : null);
        return ResponseEntity.ok(new ActionResult(true, "Участник обновлён.", null));
    }

    @GetMapping("/invitations")
    public ResponseEntity<List<InvitationDto>> invitations(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        List<InvitationDto> list = invitationService.findByTeamId(teamId).stream()
                .map(inv -> new InvitationDto(
                        inv.getCode(),
                        invitationService.buildInviteLink(inv.getCode()),
                        inv.getRole().name(),
                        inv.getExpiresAt().toString()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/invitations")
    public ResponseEntity<InvitationCreateResponse> createInvitation(@RequestBody InvitationCreateRequest body,
                                                                     HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        String roleStr = body != null && body.role() != null ? body.role().toUpperCase() : "PLAYER";
        int days = body != null && body.expiresInDays() != null ? body.expiresInDays() : 7;
        try {
            TeamMember.Role role = TeamMember.Role.valueOf(roleStr);
            Invitation inv = invitationService.create(teamId, role, days);
            String link = invitationService.buildInviteLink(inv.getCode());
            return ResponseEntity.ok(new InvitationCreateResponse(true, new InvitationDto(inv.getCode(), link, inv.getRole().name(), inv.getExpiresAt().toString()), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new InvitationCreateResponse(false, null, "Роль: ADMIN или PLAYER"));
        }
    }

    @DeleteMapping("/invitations/{code}")
    public ResponseEntity<ActionResult> deleteInvitation(@PathVariable String code, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        boolean deleted = invitationService.deleteByCode(teamId, code);
        if (deleted) return ResponseEntity.ok(new ActionResult(true, "Приглашение удалено.", null));
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/matches")
    public ResponseEntity<List<MatchDto>> matches(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(matchService.findByTeamIdOrderByDateDesc(teamId).stream().map(this::toMatchDto).collect(Collectors.toList()));
    }

    @PostMapping("/matches")
    public ResponseEntity<ActionResult> createMatch(@RequestBody MatchCreateDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        try {
            if (dto.opponent() == null || dto.opponent().isBlank()) {
                return ResponseEntity.badRequest().body(new ActionResult(false, null, "Укажи соперника."));
            }
            Instant date = dto.date() != null && !dto.date().isBlank() && dto.time() != null && !dto.time().isBlank()
                    ? LocalDateTime.parse(dto.date() + "T" + dto.time()).atZone(ZoneId.systemDefault()).toInstant()
                    : Instant.now();
            matchService.createMatch(teamId, dto.opponent(), date, dto.location());
            return ResponseEntity.ok(new ActionResult(true, "Матч создан.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, e.getMessage()));
        }
    }

    @GetMapping("/matches/{id}")
    public ResponseEntity<MatchDto> getMatch(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        return matchService.findByIdAndTeamId(id, teamId)
                .map(m -> ResponseEntity.ok(toMatchDto(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/matches/{id}")
    public ResponseEntity<ActionResult> updateMatch(@PathVariable Long id, @RequestBody MatchUpdateDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        try {
            Instant date = dto.date() != null && dto.time() != null && !dto.date().isBlank() && !dto.time().isBlank()
                    ? LocalDateTime.parse(dto.date() + "T" + dto.time()).atZone(ZoneId.systemDefault()).toInstant()
                    : null;
            matchService.updateMatch(teamId, id, dto.opponent(), date, dto.location());
            return ResponseEntity.ok(new ActionResult(true, "Матч обновлён.", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, e.getMessage()));
        }
    }

    @PostMapping("/matches/{id}/result")
    public ResponseEntity<ActionResult> setResult(@PathVariable Long id, @RequestBody ResultDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        try {
            Match match = matchService.setResult(id, teamId, dto.ourScore(), dto.opponentScore());
            Team team = teamService.findById(teamId).orElseThrow();
            String postText = matchPostService.buildPostText(team, match);
            return ResponseEntity.ok(new ActionResult(true, "Результат сохранён.", postText));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, e.getMessage()));
        }
    }

    @PostMapping("/matches/{id}/cancel")
    public ResponseEntity<ActionResult> cancelMatch(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        try {
            matchService.cancelMatch(teamId, id);
            return ResponseEntity.ok(new ActionResult(true, "Матч отменён.", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, e.getMessage()));
        }
    }

    @GetMapping("/matches/{id}/stats")
    public ResponseEntity<MatchStatsDto> getMatchStats(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        if (matchService.findByIdAndTeamId(id, teamId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Player> players = playerService.findByTeamId(teamId);
        List<MatchPlayerStat> stats = matchPlayerStatService.findByMatchId(id);
        List<MatchStatRowDto> rows = players.stream()
                .map(p -> {
                    MatchPlayerStat stat = stats.stream()
                            .filter(s -> s.getPlayer().getId().equals(p.getId()))
                            .findFirst()
                            .orElse(null);
                    return new MatchStatRowDto(
                            p.getId(),
                            p.getName(),
                            p.getNumber(),
                            stat != null ? stat.getMinutes() : null,
                            stat != null ? stat.getPoints() : 0,
                            stat != null ? stat.getRebounds() : 0,
                            stat != null ? stat.getAssists() : 0,
                            stat != null ? stat.getFouls() : 0,
                            stat != null ? stat.getPlusMinus() : null,
                            stat != null && stat.isMvp()
                    );
                })
                .collect(Collectors.toList());
        Optional<Player> mvp = matchPlayerStatService.findMvpForMatch(id);
        return ResponseEntity.ok(new MatchStatsDto(rows, mvp.map(Player::getName).orElse(null)));
    }

    @PostMapping("/matches/{id}/stats")
    public ResponseEntity<ActionResult> saveMatchStats(@PathVariable Long id, @RequestBody MatchStatsSaveDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        if (matchService.findByIdAndTeamId(id, teamId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<MatchPlayerStatService.StatEntry> entries = dto.stats() != null ? dto.stats().stream()
                .map(s -> new MatchPlayerStatService.StatEntry(
                        s.playerId(), s.minutes(), s.points(), s.rebounds(), s.assists(), s.fouls(), s.plusMinus(), s.mvp()))
                .collect(Collectors.toList()) : List.of();
        matchPlayerStatService.saveStats(id, teamId, entries);
        return ResponseEntity.ok(new ActionResult(true, "Статистика сохранена.", null));
    }

    @GetMapping("/matches/{id}/card")
    public ResponseEntity<byte[]> matchCard(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.notFound().build();
        Optional<Match> opt = matchService.findByIdAndTeamId(id, teamId);
        if (opt.isEmpty() || opt.get().getOurScore() == null || opt.get().getOpponentScore() == null) {
            return ResponseEntity.notFound().build();
        }
        Team team = teamService.findById(teamId).orElse(null);
        byte[] png = matchImageService.generateScoreCard(team, opt.get());
        if (png.length == 0) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDispositionFormData("attachment", "result.png");
        return ResponseEntity.ok().headers(headers).body(png);
    }

    @GetMapping("/matches/{id}/player-card")
    public ResponseEntity<byte[]> playerCard(@PathVariable Long id, @RequestParam Long playerId, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.notFound().build();
        Optional<Match> matchOpt = matchService.findByIdAndTeamId(id, teamId);
        if (matchOpt.isEmpty()) return ResponseEntity.notFound().build();
        Optional<Player> playerOpt = playerService.findByIdAndTeamId(playerId, teamId);
        if (playerOpt.isEmpty()) return ResponseEntity.notFound().build();
        Optional<MatchPlayerStat> statOpt = matchPlayerStatService.findByMatchId(id).stream()
                .filter(s -> s.getPlayer().getId().equals(playerId))
                .findFirst();
        if (statOpt.isEmpty()) return ResponseEntity.notFound().build();
        Team team = teamService.findById(teamId).orElse(null);
        byte[] png = matchImageService.generatePlayerCard(team, matchOpt.get(), playerOpt.get(), statOpt.get());
        if (png.length == 0) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDispositionFormData("attachment", "player-card.png");
        return ResponseEntity.ok().headers(headers).body(png);
    }

    @PostMapping("/matches/{id}/send-to-channel")
    public ResponseEntity<ActionResult> sendToChannel(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        Team team = teamService.findById(teamId).orElse(null);
        if (team == null || team.getChannelTelegramChatId() == null || team.getChannelTelegramChatId().isBlank()) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Сначала укажи канал в Настройках."));
        }
        Optional<Match> opt = matchService.findByIdAndTeamId(id, teamId);
        if (opt.isEmpty() || opt.get().getOurScore() == null) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Матч не найден или результат не введён."));
        }
        byte[] png = matchImageService.generateScoreCard(team, opt.get());
        if (png.length == 0) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Не удалось сгенерировать картинку."));
        }
        try {
            InputFile photo = new InputFile(new ByteArrayInputStream(png), "result.png");
            telegramClient.execute(SendPhoto.builder()
                    .chatId(team.getChannelTelegramChatId())
                    .photo(photo)
                    .build());
            return ResponseEntity.ok(new ActionResult(true, "Опубликовано в канал.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Не удалось отправить: " + e.getMessage()));
        }
    }

    @GetMapping("/debt")
    public ResponseEntity<DebtListDto> debt(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        List<Player> debtors = playerService.findWithDebt(teamId);
        return ResponseEntity.ok(new DebtListDto(debtors.stream().map(this::toPlayerDto).collect(Collectors.toList())));
    }

    @PostMapping("/debt")
    public ResponseEntity<ActionResult> setDebt(@RequestBody DebtSetDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        try {
            BigDecimal amount = dto.amount() != null && !dto.amount().trim().isEmpty()
                    ? new BigDecimal(dto.amount().trim().replace(",", ".")) : BigDecimal.ZERO;
            playerService.setDebt(teamId, dto.playerName(), amount);
            return ResponseEntity.ok(new ActionResult(true, "Долг выставлен.", null));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Сумма должна быть числом."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, e.getMessage()));
        }
    }

    @PostMapping("/notify")
    public ResponseEntity<ActionResult> notifyTeam(@RequestBody NotifyRequest body, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        Team team = teamService.findById(teamId).orElse(null);
        if (team == null) return ResponseEntity.status(403).build();
        String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                ? team.getGroupTelegramChatId()
                : team.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Укажите групповой чат в Настройках или добавьте бота в чат команды и напишите там /start."));
        }
        String message = body != null && body.message() != null ? body.message().trim() : "";
        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Введите текст уведомления."));
        }
        if (message.length() > 4000) message = message.substring(0, 4000);
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text("📢 " + message).build());
            return ResponseEntity.ok(new ActionResult(true, "Уведомление отправлено в чат команды.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Не удалось отправить: " + e.getMessage()));
        }
    }

    @PostMapping("/debt/paid/{playerId}")
    public ResponseEntity<ActionResult> debtPaid(@PathVariable Long playerId, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        try {
            playerService.clearDebtByPlayerId(teamId, playerId);
            return ResponseEntity.ok(new ActionResult(true, "Оплата отмечена.", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, e.getMessage()));
        }
    }

    @PostMapping("/notify-debt")
    public ResponseEntity<ActionResult> notifyDebt(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        Team team = teamService.findById(teamId).orElse(null);
        if (team == null) return ResponseEntity.status(403).build();
        String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                ? team.getGroupTelegramChatId()
                : team.getTelegramChatId();
        if (chatId == null || chatId.isBlank()) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Укажите групповой чат в Настройках или добавьте бота в чат команды."));
        }
        List<Player> debtors = playerService.findWithDebt(teamId);
        if (debtors.isEmpty()) {
            return ResponseEntity.ok(new ActionResult(true, "Нет должников. Напоминание не отправлено.", null));
        }
        StringBuilder sb = new StringBuilder("💰 Напоминание о взносе.\n\nДолжники:\n");
        for (Player p : debtors) {
            sb.append("• ").append(p.getName() != null ? p.getName() : "—");
            if (p.getNumber() != null) sb.append(" №").append(p.getNumber());
            sb.append(" — ").append(p.getDebt() != null ? p.getDebt().stripTrailingZeros().toPlainString() : "0").append(" ₽\n");
        }
        sb.append("\nПросьба оплатить до следующей игры.");
        String message = sb.toString();
        if (message.length() > 4000) message = message.substring(0, 4000);
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(message).build());
            return ResponseEntity.ok(new ActionResult(true, "Напоминание о взносе отправлено в чат команды.", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Не удалось отправить: " + e.getMessage()));
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<SettingsDto> settings(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        Team team = teamService.findById(teamId).orElse(null);
        if (team == null) return ResponseEntity.status(403).build();
        String channel = team.getChannelTelegramChatId() != null ? team.getChannelTelegramChatId() : "";
        String group = team.getGroupTelegramChatId() != null ? team.getGroupTelegramChatId() : "";
        return ResponseEntity.ok(new SettingsDto(channel, group));
    }

    @PostMapping("/settings")
    public ResponseEntity<ActionResult> saveSettings(@RequestBody SettingsDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        teamService.setChannelChatId(teamId, dto.channelId());
        teamService.setGroupChatId(teamId, dto != null ? dto.groupChatId() : null);
        return ResponseEntity.ok(new ActionResult(true, "Настройки сохранены.", null));
    }

    @GetMapping("/system-settings")
    public ResponseEntity<SystemSettingsDto> systemSettings() {
        String adminUsername = systemSettingsService.getAdminTelegramUsername();
        return ResponseEntity.ok(new SystemSettingsDto(adminUsername != null ? adminUsername : ""));
    }

    @PutMapping("/system-settings")
    public ResponseEntity<ActionResult> saveSystemSettings(@RequestBody SystemSettingsDto dto) {
        systemSettingsService.setAdminTelegramUsername(dto.adminTelegramUsername());
        return ResponseEntity.ok(new ActionResult(true, "Сохранено.", null));
    }

    @GetMapping("/finance")
    public ResponseEntity<List<FinanceEntryDto>> finance(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        List<FinanceEntryDto> list = financeEntryService.findByTeamId(teamId).stream()
                .map(e -> new FinanceEntryDto(e.getId(), e.getType().name(), e.getAmount(), e.getDescription(), e.getEntryDate().toString()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/finance")
    public ResponseEntity<ActionResult> createFinanceEntry(@RequestBody FinanceCreateDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        if (dto == null || dto.type() == null || dto.type().isBlank()) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Укажите тип: INCOME или EXPENSE"));
        }
        FinanceEntry.Type type;
        try {
            type = FinanceEntry.Type.valueOf(dto.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Тип: INCOME или EXPENSE"));
        }
        BigDecimal amount = dto.amount() != null ? dto.amount() : BigDecimal.ZERO;
        LocalDate date = dto.entryDate() != null && !dto.entryDate().isBlank()
                ? LocalDate.parse(dto.entryDate())
                : LocalDate.now();
        financeEntryService.create(teamId, type, amount, dto.description(), date);
        return ResponseEntity.ok(new ActionResult(true, "Запись добавлена.", null));
    }

    @DeleteMapping("/finance/{id}")
    public ResponseEntity<ActionResult> deleteFinanceEntry(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        financeEntryService.deleteByIdAndTeamId(id, teamId);
        return ResponseEntity.ok(new ActionResult(true, "Запись удалена.", null));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventDto>> events(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        List<EventDto> list = eventService.findByTeamId(teamId).stream()
                .map(e -> new EventDto(e.getId(), e.getTitle(), e.getEventType().name(), e.getEventDate().toString(),
                        e.getLocation(), e.getDescription()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/events")
    public ResponseEntity<ActionResult> createEvent(@RequestBody EventCreateDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        if (dto == null || dto.title() == null || dto.title().isBlank()) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Укажите название события"));
        }
        Event.EventType type = Event.EventType.TRAINING;
        if (dto.eventType() != null && !dto.eventType().isBlank()) {
            try {
                type = Event.EventType.valueOf(dto.eventType().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        Instant eventDate = dto.eventDate() != null && !dto.eventDate().isBlank()
                ? Instant.parse(dto.eventDate())
                : Instant.now();
        Event event = eventService.create(teamId, dto.title(), type, eventDate, dto.location(), dto.description());
        Team team = teamService.findById(teamId).orElse(null);
        if (team != null) {
            String chatId = (team.getGroupTelegramChatId() != null && !team.getGroupTelegramChatId().isBlank())
                    ? team.getGroupTelegramChatId()
                    : team.getTelegramChatId();
            if (chatId != null && !chatId.isBlank()) {
                String timeStr = event.getEventDate().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                String loc = (event.getLocation() != null && !event.getLocation().isBlank()) ? "\n🏟️ " + event.getLocation() : "";
                String msg = "[НОВОЕ СОБЫТИЕ]\n" + (type == Event.EventType.TRAINING ? "🏋️ " : "🏀 ") + event.getTitle() + "\n📅 " + timeStr + loc;
                try {
                    telegramClient.execute(SendMessage.builder().chatId(chatId).text(msg).build());
                } catch (Exception ignored) {
                }
            }
        }
        return ResponseEntity.ok(new ActionResult(true, "Событие создано и отправлено в чат команды.", null));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<ActionResult> deleteEvent(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        eventService.deleteByIdAndTeamId(id, teamId);
        return ResponseEntity.ok(new ActionResult(true, "Событие удалено.", null));
    }

    @GetMapping("/finance/report")
    public ResponseEntity<FinanceReportDto> financeReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        LocalDate fromDate = (from != null && !from.isBlank()) ? LocalDate.parse(from) : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = (to != null && !to.isBlank()) ? LocalDate.parse(to) : LocalDate.now();
        if (fromDate.isAfter(toDate)) {
            LocalDate t = fromDate;
            fromDate = toDate;
            toDate = t;
        }
        BigDecimal income = financeEntryService.totalIncome(teamId, fromDate, toDate);
        BigDecimal expense = financeEntryService.totalExpense(teamId, fromDate, toDate);
        BigDecimal balance = income.subtract(expense);
        List<FinanceEntryDto> entries = financeEntryService.findByTeamIdAndDateBetween(teamId, fromDate, toDate).stream()
                .map(e -> new FinanceEntryDto(e.getId(), e.getType().name(), e.getAmount(), e.getDescription(), e.getEntryDate().toString()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(new FinanceReportDto(fromDate.toString(), toDate.toString(), income, expense, balance, entries));
    }

    private TeamDto toTeamDto(Team t) {
        return new TeamDto(t.getId(), t.getName(), t.getTelegramChatId(), t.getChannelTelegramChatId());
    }

    private PlayerDto toPlayerDto(Player p) {
        return new PlayerDto(p.getId(), p.getName(), p.getNumber(), p.getPlayerStatus().name(),
                p.getDebt() != null ? p.getDebt() : BigDecimal.ZERO);
    }

    private MatchDto toMatchDto(Match m) {
        String dateStr = m.getDate() != null ? DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(m.getDate().atZone(ZoneId.systemDefault()).toLocalDateTime()) : null;
        return new MatchDto(m.getId(), m.getOpponent(), dateStr, m.getOurScore(), m.getOpponentScore(),
                m.getLocation(), m.getStatus().name());
    }

    // ——— DTOs (records) ———
    public record LoginRequest(String username, String password) {}
    public record LoginResponse(boolean success, String error) {}
    public record TeamSelectRequest(Long teamId) {}
    public record TeamSelectResponse(boolean success, String error) {}
    public record MeResponse(List<TeamDto> teams, TeamDto currentTeam) {}
    public record TeamDto(Long id, String name, String telegramChatId, String channelTelegramChatId) {}
    public record DashboardDto(TeamDto team, int playerCount, int debtorCount, java.math.BigDecimal totalDebt, MatchDto nextMatch) {}
    public record PlayerDto(Long id, String name, Integer number, String status, java.math.BigDecimal debt) {}
    public record PlayerCreateDto(String name, Integer number) {}
    public record PlayerUpdateDto(String name, Integer number, String status) {}
    public record MatchDto(Long id, String opponent, String date, Integer ourScore, Integer opponentScore, String location, String status) {}
    public record MatchCreateDto(String opponent, String date, String time, String location) {}
    public record MatchUpdateDto(String opponent, String date, String time, String location) {}
    public record ResultDto(int ourScore, int opponentScore) {}
    public record ActionResult(boolean success, String message, String data) {}
    public record DebtListDto(List<PlayerDto> debtors) {}
    public record DebtSetDto(String playerName, String amount) {}
    public record SettingsDto(String channelId, String groupChatId) {}
    public record MemberDto(String telegramUserId, String telegramUsername, String displayName, String role,
                            Integer number, String status, java.math.BigDecimal debt, boolean isActive) {}
    public record MemberUpdateRequest(String displayName, String role, Boolean isActive, Integer number, String status, java.math.BigDecimal debt) {}
    public record SystemSettingsDto(String adminTelegramUsername) {}
    public record InvitationDto(String code, String link, String role, String expiresAt) {}
    public record InvitationCreateRequest(String role, Integer expiresInDays) {}
    public record InvitationCreateResponse(boolean success, InvitationDto invitation, String error) {}
    public record NotifyRequest(String message) {}
    public record FinanceEntryDto(Long id, String type, BigDecimal amount, String description, String entryDate) {}
    public record FinanceCreateDto(String type, BigDecimal amount, String description, String entryDate) {}
    public record FinanceReportDto(String from, String to, BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal balance, List<FinanceEntryDto> entries) {}
    public record EventDto(Long id, String title, String eventType, String eventDate, String location, String description) {}
    public record EventCreateDto(String title, String eventType, String eventDate, String location, String description) {}

    @GetMapping("/league-table")
    public ResponseEntity<List<LeagueTableRowDto>> leagueTable(HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        List<LeagueTableRowDto> list = leagueTableService.findByTeamId(teamId).stream()
                .map(r -> new LeagueTableRowDto(r.getId(), r.getPosition(), r.getTeamName(), r.getWins(), r.getLosses(), r.getPointsDiff()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/league-table")
    public ResponseEntity<ActionResult> addLeagueTableRow(@RequestBody LeagueTableRowCreateDto dto, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        if (dto == null || dto.teamName() == null || dto.teamName().isBlank()) {
            return ResponseEntity.badRequest().body(new ActionResult(false, null, "Укажите название команды"));
        }
        leagueTableService.create(teamId, dto.position() != null ? dto.position() : 1, dto.teamName(), dto.wins() != null ? dto.wins() : 0, dto.losses() != null ? dto.losses() : 0, dto.pointsDiff() != null ? dto.pointsDiff() : 0);
        return ResponseEntity.ok(new ActionResult(true, "Строка добавлена.", null));
    }

    @DeleteMapping("/league-table/{id}")
    public ResponseEntity<ActionResult> deleteLeagueTableRow(@PathVariable Long id, HttpSession session) {
        Long teamId = requireTeamId(session);
        if (teamId == null) return ResponseEntity.status(403).build();
        leagueTableService.deleteByIdAndTeamId(id, teamId);
        return ResponseEntity.ok(new ActionResult(true, "Удалено.", null));
    }

    public record LeagueTableRowDto(Long id, int position, String teamName, int wins, int losses, int pointsDiff) {}
    public record LeagueTableRowCreateDto(Integer position, String teamName, Integer wins, Integer losses, Integer pointsDiff) {}
    public record MatchStatRowDto(Long playerId, String playerName, Integer number, Integer minutes, int points, int rebounds, int assists, int fouls, Integer plusMinus, boolean mvp) {}
    public record MatchStatsDto(List<MatchStatRowDto> stats, String mvpPlayerName) {}
    public record MatchStatsSaveDto(List<MatchStatRowSaveDto> stats) {}
    public record MatchStatRowSaveDto(Long playerId, Integer minutes, Integer points, Integer rebounds, Integer assists, Integer fouls, Integer plusMinus, Boolean mvp) {}
}
