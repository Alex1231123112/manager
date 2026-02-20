package com.basketbot.service;

import com.basketbot.model.IntegrationEvent;
import com.basketbot.repository.IntegrationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Запись и отдача метрик интеграции с Telegram: доставка сообщений, ошибки, типы событий.
 */
@Service
public class IntegrationMetricsService {

    private final IntegrationEventRepository repository;

    public IntegrationMetricsService(IntegrationEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(IntegrationEvent.EventType eventType, String targetChatId, boolean success,
                       String errorMessage, Long teamId, Long matchId) {
        IntegrationEvent e = new IntegrationEvent();
        e.setEventType(eventType);
        e.setTargetChatId(targetChatId != null && targetChatId.length() > 50 ? targetChatId.substring(0, 50) : targetChatId);
        e.setSuccess(success);
        e.setErrorMessage(errorMessage != null && errorMessage.length() > 2000 ? errorMessage.substring(0, 2000) : errorMessage);
        e.setTeamId(teamId);
        e.setMatchId(matchId);
        repository.save(e);
    }

    @Transactional(readOnly = true)
    public List<IntegrationEvent> getRecentEvents(int limit) {
        if (limit <= 0 || limit > 200) limit = 100;
        return repository.findTop100ByOrderByCreatedAtDesc().stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(Instant from, Instant to) {
        if (from == null) from = Instant.now().minus(7, ChronoUnit.DAYS);
        if (to == null) to = Instant.now();
        List<IntegrationEvent> events = repository.findByCreatedAtBetween(from, to);
        long total = events.size();
        long successCount = events.stream().filter(IntegrationEvent::isSuccess).count();
        long failCount = total - successCount;

        List<Map<String, Object>> byType = new ArrayList<>();
        for (IntegrationEvent.EventType type : IntegrationEvent.EventType.values()) {
            long ok = events.stream().filter(ev -> ev.getEventType() == type && ev.isSuccess()).count();
            long fail = events.stream().filter(ev -> ev.getEventType() == type && !ev.isSuccess()).count();
            if (ok > 0 || fail > 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("eventType", type.name());
                row.put("label", eventTypeLabel(type));
                row.put("success", ok);
                row.put("failed", fail);
                byType.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("total", total);
        result.put("success", successCount);
        result.put("failed", failCount);
        result.put("byType", byType);
        return result;
    }

    private static String eventTypeLabel(IntegrationEvent.EventType type) {
        return switch (type) {
            case BOT_MESSAGE -> "Ответ бота";
            case REMINDER_24H -> "Напоминание за 24 ч";
            case REMINDER_3H -> "Напоминание за 3 ч";
            case REMINDER_STATS -> "Статистика подтверждений";
            case REMINDER_AFTER_MATCH -> "После матча (результат)";
            case DEBT_REMINDER -> "Напоминание о долгах";
            case INVITE_QR -> "QR приглашения";
            case POLL -> "Опрос";
            case CHANNEL_POST -> "Публикация в канал";
        };
    }
}
