package com.basketbot.service;

import com.basketbot.model.EventAttendance;
import com.basketbot.model.Match;
import com.basketbot.model.TeamMember;
import com.basketbot.repository.EventAttendanceRepository;
import com.basketbot.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventAttendanceService {

    private final EventAttendanceRepository eventAttendanceRepository;
    private final MatchRepository matchRepository;
    private final TeamMemberService teamMemberService;
    private final MatchService matchService;

    public EventAttendanceService(EventAttendanceRepository eventAttendanceRepository,
                                  MatchRepository matchRepository,
                                  TeamMemberService teamMemberService,
                                  MatchService matchService) {
        this.eventAttendanceRepository = eventAttendanceRepository;
        this.matchRepository = matchRepository;
        this.teamMemberService = teamMemberService;
        this.matchService = matchService;
    }

    /** Сохранить или обновить ответ участника на событие (матч). */
    @Transactional
    public void setAttendance(Long matchId, String telegramUserId, EventAttendance.Status status) {
        if (matchId == null || telegramUserId == null || telegramUserId.isBlank() || status == null) return;
        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null) return;
        eventAttendanceRepository.findByMatchIdAndTelegramUserId(matchId, telegramUserId)
                .ifPresentOrElse(
                        a -> {
                            a.setStatus(status);
                            eventAttendanceRepository.save(a);
                        },
                        () -> {
                            EventAttendance a = new EventAttendance();
                            a.setMatch(match);
                            a.setTelegramUserId(telegramUserId);
                            a.setStatus(status);
                            eventAttendanceRepository.save(a);
                        }
                );
    }

    /** Статистика по матчу: количество по каждому статусу и не ответивших (всего участников - ответивших). */
    @Transactional(readOnly = true)
    public Map<EventAttendance.Status, Long> getCountsByStatus(Long matchId) {
        Map<EventAttendance.Status, Long> counts = new HashMap<>();
        for (EventAttendance.Status s : EventAttendance.Status.values()) {
            counts.put(s, 0L);
        }
        List<EventAttendance> list = eventAttendanceRepository.findByMatchId(matchId);
        for (EventAttendance a : list) {
            counts.merge(a.getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public int getRespondedCount(Long matchId) {
        return eventAttendanceRepository.findByMatchId(matchId).size();
    }

    /** Состав матча по подтверждениям: кто ответил (с именем и статусом) и кто не ответил. */
    @Transactional(readOnly = true)
    public MatchAttendanceDto getAttendanceForMatch(Long matchId, Long teamId) {
        Optional<Match> matchOpt = matchService.findByIdAndTeamId(matchId, teamId);
        if (matchOpt.isEmpty()) return new MatchAttendanceDto(List.of(), List.of());
        Match match = matchOpt.get();
        List<TeamMember> allMembers = teamMemberService.findByTeamId(teamId).stream()
                .filter(TeamMember::isActive).toList();
        List<EventAttendance> responded = eventAttendanceRepository.findByMatchId(matchId);
        Set<String> respondedIds = responded.stream().map(EventAttendance::getTelegramUserId).collect(Collectors.toSet());
        Map<String, TeamMember> memberByTelegramId = allMembers.stream()
                .collect(Collectors.toMap(TeamMember::getTelegramUserId, m -> m, (a, b) -> a));
        List<MatchAttendanceDto.Row> coming = new ArrayList<>();
        List<MatchAttendanceDto.Row> noResponse = new ArrayList<>();
        for (EventAttendance a : responded) {
            TeamMember m = memberByTelegramId.get(a.getTelegramUserId());
            String name = m != null && m.getDisplayName() != null && !m.getDisplayName().isBlank()
                    ? m.getDisplayName().trim() : (m != null && m.getTelegramUsername() != null ? "@" + m.getTelegramUsername() : a.getTelegramUserId());
            String username = m != null && m.getTelegramUsername() != null && !m.getTelegramUsername().isBlank()
                    ? ("@" + m.getTelegramUsername().replaceFirst("^@", "")) : null;
            coming.add(new MatchAttendanceDto.Row(a.getTelegramUserId(), name, username, a.getStatus().name()));
        }
        for (TeamMember m : allMembers) {
            if (respondedIds.contains(m.getTelegramUserId())) continue;
            String name = m.getDisplayName() != null && !m.getDisplayName().isBlank()
                    ? m.getDisplayName().trim() : (m.getTelegramUsername() != null ? "@" + m.getTelegramUsername() : m.getTelegramUserId());
            String username = m.getTelegramUsername() != null && !m.getTelegramUsername().isBlank()
                    ? ("@" + m.getTelegramUsername().replaceFirst("^@", "")) : null;
            noResponse.add(new MatchAttendanceDto.Row(m.getTelegramUserId(), name, username, null));
        }
        return new MatchAttendanceDto(coming, noResponse);
    }

    /** Подтверждения участника по будущим матчам команды (для профиля в админке). */
    @Transactional(readOnly = true)
    public List<MemberAttendanceDto> getAttendanceForMember(String telegramUserId, Long teamId) {
        List<Match> future = matchService.findFutureByTeamId(teamId);
        List<MemberAttendanceDto> result = new ArrayList<>();
        for (Match m : future) {
            Optional<EventAttendance> a = eventAttendanceRepository.findByMatchIdAndTelegramUserId(m.getId(), telegramUserId);
            result.add(new MemberAttendanceDto(m.getId(), m.getOpponent(), m.getDate(),
                    a.map(att -> att.getStatus().name()).orElse(null)));
        }
        return result;
    }

    public record MatchAttendanceDto(List<Row> responded, List<Row> noResponse) {
        public record Row(String telegramUserId, String displayName, String telegramUsername, String status) {}
    }

    public record MemberAttendanceDto(Long matchId, String opponent, Instant date, String status) {}
}
