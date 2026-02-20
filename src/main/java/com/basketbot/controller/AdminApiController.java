package com.basketbot.controller;

import com.basketbot.model.EventAttendance;
import com.basketbot.model.Invitation;
import com.basketbot.model.Team;
import com.basketbot.model.TeamMember;
import com.basketbot.service.EventAttendanceService;
import com.basketbot.service.IntegrationMetricsService;
import com.basketbot.service.InvitationService;
import com.basketbot.service.MatchService;
import com.basketbot.service.SystemSettingsService;
import com.basketbot.service.TeamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API для веб-админки: логин, выбор команды, настройки, приглашения.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private static final String SESSION_CURRENT_TEAM_ID = "adminCurrentTeamId";

    private final AuthenticationManager authenticationManager;
    private final TeamService teamService;
    private final SystemSettingsService systemSettingsService;
    private final InvitationService invitationService;
    private final EventAttendanceService eventAttendanceService;
    private final MatchService matchService;
    private final IntegrationMetricsService integrationMetricsService;

    public AdminApiController(AuthenticationManager authenticationManager,
                             TeamService teamService,
                             SystemSettingsService systemSettingsService,
                             InvitationService invitationService,
                             EventAttendanceService eventAttendanceService,
                             MatchService matchService,
                             IntegrationMetricsService integrationMetricsService) {
        this.authenticationManager = authenticationManager;
        this.teamService = teamService;
        this.systemSettingsService = systemSettingsService;
        this.invitationService = invitationService;
        this.eventAttendanceService = eventAttendanceService;
        this.matchService = matchService;
        this.integrationMetricsService = integrationMetricsService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body != null ? body.get("username") : null;
        String password = body != null ? body.get("password") : null;
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Укажите логин и пароль"));
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            request.getSession(true);
            SecurityContextHolder.getContext().setAuthentication(auth);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Неверный логин или пароль"));
        }
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        List<Team> teams = teamService.findAll();
        List<Map<String, Object>> teamDtos = teams.stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "name", t.getName(),
                        "telegramChatId", (Object) (t.getTelegramChatId() != null ? t.getTelegramChatId() : null),
                        "channelTelegramChatId", (Object) (t.getChannelTelegramChatId() != null ? t.getChannelTelegramChatId() : null)))
                .collect(Collectors.toList());
        Long currentId = session != null ? (Long) session.getAttribute(SESSION_CURRENT_TEAM_ID) : null;
        Team currentTeam = currentId != null ? teamService.findById(currentId).orElse(null) : null;
        if (currentTeam == null && !teams.isEmpty()) {
            currentTeam = teams.get(0);
            if (session != null) {
                session.setAttribute(SESSION_CURRENT_TEAM_ID, currentTeam.getId());
            }
        }
        Map<String, Object> currentTeamDto = null;
        if (currentTeam != null) {
            currentTeamDto = Map.of(
                    "id", currentTeam.getId(),
                    "name", currentTeam.getName(),
                    "telegramChatId", (Object) (currentTeam.getTelegramChatId() != null ? currentTeam.getTelegramChatId() : null),
                    "channelTelegramChatId", (Object) (currentTeam.getChannelTelegramChatId() != null ? currentTeam.getChannelTelegramChatId() : null));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("teams", teamDtos);
        result.put("currentTeam", currentTeamDto);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/team-select", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> teamSelect(@RequestBody Map<String, Long> body, HttpSession session) {
        Long teamId = body != null ? body.get("teamId") : null;
        if (teamId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "teamId required"));
        }
        if (teamService.findById(teamId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Team not found"));
        }
        if (session != null) {
            session.setAttribute(SESSION_CURRENT_TEAM_ID, teamId);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/system-settings")
    public ResponseEntity<Map<String, String>> getSystemSettings() {
        String adminId = systemSettingsService.getAdminTelegramId();
        String adminUsername = systemSettingsService.getAdminTelegramUsername();
        return ResponseEntity.ok(Map.of(
                "adminTelegramId", adminId != null ? adminId : "",
                "adminTelegramUsername", adminUsername != null ? adminUsername : ""));
    }

    @PutMapping(value = "/system-settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> putSystemSettings(@RequestBody Map<String, String> body) {
        if (body != null && body.containsKey("adminTelegramId")) {
            systemSettingsService.setAdminTelegramId(body.get("adminTelegramId"));
        }
        if (body != null && body.containsKey("adminTelegramUsername")) {
            systemSettingsService.setAdminTelegramUsername(body.get("adminTelegramUsername"));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Сохранено"));
    }

    private Long getCurrentTeamId(HttpSession session) {
        if (session == null) return null;
        Long id = (Long) session.getAttribute(SESSION_CURRENT_TEAM_ID);
        if (id != null && teamService.findById(id).isPresent()) return id;
        teamService.findAll().stream().findFirst().ifPresent(t -> session.setAttribute(SESSION_CURRENT_TEAM_ID, t.getId()));
        return (Long) session.getAttribute(SESSION_CURRENT_TEAM_ID);
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getSettings(HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.ok(Map.of("channelId", "", "groupChatId", ""));
        }
        Team team = teamService.findById(teamId).orElse(null);
        if (team == null) {
            return ResponseEntity.ok(Map.of("channelId", "", "groupChatId", ""));
        }
        return ResponseEntity.ok(Map.of(
                "channelId", team.getChannelTelegramChatId() != null ? team.getChannelTelegramChatId() : "",
                "groupChatId", team.getGroupTelegramChatId() != null ? team.getGroupTelegramChatId() : ""));
    }

    @PostMapping(value = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> postSettings(@RequestBody Map<String, String> body, HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "data", "Выберите команду"));
        }
        String channelId = body != null ? body.get("channelId") : null;
        String groupChatId = body != null ? body.get("groupChatId") : null;
        teamService.setChannelChatId(teamId, channelId);
        teamService.setGroupChatId(teamId, groupChatId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Настройки сохранены"));
    }

    @GetMapping("/invitations")
    public ResponseEntity<List<Map<String, Object>>> getInvitations(HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.ok(List.of());
        }
        List<Invitation> list = invitationService.findByTeamId(teamId);
        List<Map<String, Object>> dtos = list.stream()
                .map(inv -> Map.<String, Object>of(
                        "code", inv.getCode(),
                        "link", invitationService.buildInviteLink(inv.getCode()),
                        "role", inv.getRole().name(),
                        "expiresAt", inv.getExpiresAt().toString()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping(value = "/invitations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> postInvitation(@RequestBody Map<String, Object> body, HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Выберите команду", "invitation", (Object) null));
        }
        String roleStr = body != null && body.get("role") != null ? body.get("role").toString() : "PLAYER";
        int days = 7;
        if (body != null && body.get("expiresInDays") != null) {
            if (body.get("expiresInDays") instanceof Number) {
                days = ((Number) body.get("expiresInDays")).intValue();
            } else {
                try {
                    days = Integer.parseInt(body.get("expiresInDays").toString());
                } catch (NumberFormatException ignored) {}
            }
        }
        if (days < 1) days = 1;
        if (days > 365) days = 365;
        TeamMember.Role role;
        try {
            role = TeamMember.Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            role = TeamMember.Role.PLAYER;
        }
        try {
            Invitation inv = invitationService.create(teamId, role, days);
            Map<String, Object> dto = Map.of(
                    "code", inv.getCode(),
                    "link", invitationService.buildInviteLink(inv.getCode()),
                    "role", inv.getRole().name(),
                    "expiresAt", inv.getExpiresAt().toString());
            return ResponseEntity.ok(Map.of("success", true, "invitation", dto, "error", (Object) null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new HashMap<>(Map.of("success", false, "error", e.getMessage(), "invitation", (Object) null)));
        }
    }

    @DeleteMapping(value = "/invitations/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteInvitation(@PathVariable String code, HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Выберите команду", "data", (Object) null));
        }
        boolean deleted = invitationService.deleteByCode(teamId, code != null ? code.trim() : "");
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Приглашение не найдено", "data", (Object) null));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Приглашение удалено", "data", (Object) null));
    }

    /** Состав матча по подтверждениям (Буду / Опоздаю / Не смогу / Не ответили). */
    @GetMapping("/matches/{matchId}/attendance")
    public ResponseEntity<Map<String, Object>> getMatchAttendance(@PathVariable Long matchId, HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Выберите команду"));
        }
        EventAttendanceService.MatchAttendanceDto dto = eventAttendanceService.getAttendanceForMatch(matchId, teamId);
        List<Map<String, Object>> responded = dto.responded().stream()
                .map(r -> Map.<String, Object>of(
                        "telegramUserId", r.telegramUserId(),
                        "displayName", r.displayName() != null ? r.displayName() : "",
                        "telegramUsername", r.telegramUsername() != null ? r.telegramUsername() : "",
                        "status", r.status() != null ? r.status() : ""))
                .collect(Collectors.toList());
        List<Map<String, Object>> noResponse = dto.noResponse().stream()
                .map(r -> Map.<String, Object>of(
                        "telegramUserId", r.telegramUserId(),
                        "displayName", r.displayName() != null ? r.displayName() : "",
                        "telegramUsername", r.telegramUsername() != null ? r.telegramUsername() : ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("responded", responded, "noResponse", noResponse));
    }

    /** Установить статус участия (например NOT_COMING — отменить участие). */
    @PutMapping(value = "/matches/{matchId}/attendance", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> setMatchAttendance(
            @PathVariable Long matchId,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Выберите команду"));
        }
        if (matchService.findByIdAndTeamId(matchId, teamId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "error", "Матч не найден"));
        }
        String telegramUserId = body != null ? body.get("telegramUserId") : null;
        String statusStr = body != null ? body.get("status") : null;
        if (telegramUserId == null || telegramUserId.isBlank() || statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Укажите telegramUserId и status"));
        }
        EventAttendance.Status status;
        try {
            status = EventAttendance.Status.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Недопустимый статус"));
        }
        eventAttendanceService.setAttendance(matchId, telegramUserId.trim(), status);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Подтверждения участника по будущим матчам (для профиля в админке). */
    @GetMapping("/members/{telegramUserId}/attendance")
    public ResponseEntity<List<Map<String, Object>>> getMemberAttendance(
            @PathVariable String telegramUserId,
            HttpSession session) {
        Long teamId = getCurrentTeamId(session);
        if (teamId == null) {
            return ResponseEntity.badRequest().build();
        }
        List<EventAttendanceService.MemberAttendanceDto> list = eventAttendanceService.getAttendanceForMember(
                telegramUserId != null ? telegramUserId : "", teamId);
        List<Map<String, Object>> dtos = list.stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("matchId", d.matchId());
                    m.put("opponent", d.opponent() != null ? d.opponent() : "");
                    m.put("date", d.date() != null ? d.date().toString() : null);
                    m.put("status", d.status());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /** Метрики интеграции с Telegram: доставка сообщений за период. */
    @GetMapping("/integration/stats")
    public ResponseEntity<Map<String, Object>> getIntegrationStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpSession session) {
        Instant fromInstant = null;
        Instant toInstant = null;
        if (from != null && !from.isBlank()) {
            try {
                fromInstant = Instant.parse(from);
            } catch (Exception ignored) {}
        }
        if (to != null && !to.isBlank()) {
            try {
                toInstant = Instant.parse(to);
            } catch (Exception ignored) {}
        }
        if (fromInstant == null) fromInstant = Instant.now().minus(7, ChronoUnit.DAYS);
        if (toInstant == null) toInstant = Instant.now();
        Map<String, Object> stats = integrationMetricsService.getStats(fromInstant, toInstant);
        return ResponseEntity.ok(stats);
    }

    /** Последние события интеграции (лог отправок и ошибок). */
    @GetMapping("/integration/events")
    public ResponseEntity<List<Map<String, Object>>> getIntegrationEvents(
            @RequestParam(defaultValue = "100") int limit) {
        List<com.basketbot.model.IntegrationEvent> events = integrationMetricsService.getRecentEvents(limit);
        List<Map<String, Object>> dtos = events.stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", e.getId());
                    m.put("eventType", e.getEventType().name());
                    m.put("targetChatId", e.getTargetChatId());
                    m.put("success", e.isSuccess());
                    m.put("errorMessage", e.getErrorMessage());
                    m.put("teamId", e.getTeamId());
                    m.put("matchId", e.getMatchId());
                    m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
