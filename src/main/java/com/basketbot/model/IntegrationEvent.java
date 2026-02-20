package com.basketbot.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "integration_event")
public class IntegrationEvent {

    public enum EventType {
        /** Ответ бота пользователю (команда, кнопка) */
        BOT_MESSAGE,
        /** Напоминание за 24 ч с кнопками подтверждения */
        REMINDER_24H,
        /** Напоминание за 3 ч до матча */
        REMINDER_3H,
        /** Статистика подтверждений в чат */
        REMINDER_STATS,
        /** Напоминание после матча (результат) */
        REMINDER_AFTER_MATCH,
        /** Еженедельное напоминание о долгах */
        DEBT_REMINDER,
        /** Отправка QR приглашения */
        INVITE_QR,
        /** Опрос в чат команды */
        POLL,
        /** Публикация в канал (карточка, пост) */
        CHANNEL_POST
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "target_chat_id", length = 50)
    private String targetChatId;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getTargetChatId() { return targetChatId; }
    public void setTargetChatId(String targetChatId) { this.targetChatId = targetChatId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
