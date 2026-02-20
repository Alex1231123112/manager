package com.basketbot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "matches")
public class Match {

    public enum Status {
        SCHEDULED,
        COMPLETED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 100)
    private String opponent;

    @Column(nullable = false)
    private Instant date;

    @Column(name = "our_score")
    private Integer ourScore;

    @Column(name = "opponent_score")
    private Integer opponentScore;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.SCHEDULED;

    @Column(name = "reminder_24h_sent", nullable = false)
    private boolean reminder24hSent = false;

    @Column(name = "reminder_24h_sent_at")
    private Instant reminder24hSentAt;

    @Column(name = "reminder_stats_sent", nullable = false)
    private boolean reminderStatsSent = false;

    @Column(name = "reminder_3h_sent", nullable = false)
    private boolean reminder3hSent = false;

    @Column(name = "reminder_after_sent", nullable = false)
    private boolean reminderAfterSent = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public String getOpponent() {
        return opponent;
    }

    public void setOpponent(String opponent) {
        this.opponent = opponent;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public Integer getOurScore() {
        return ourScore;
    }

    public void setOurScore(Integer ourScore) {
        this.ourScore = ourScore;
    }

    public Integer getOpponentScore() {
        return opponentScore;
    }

    public void setOpponentScore(Integer opponentScore) {
        this.opponentScore = opponentScore;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isReminder24hSent() {
        return reminder24hSent;
    }

    public void setReminder24hSent(boolean reminder24hSent) {
        this.reminder24hSent = reminder24hSent;
    }

    public Instant getReminder24hSentAt() {
        return reminder24hSentAt;
    }

    public void setReminder24hSentAt(Instant reminder24hSentAt) {
        this.reminder24hSentAt = reminder24hSentAt;
    }

    public boolean isReminderStatsSent() {
        return reminderStatsSent;
    }

    public void setReminderStatsSent(boolean reminderStatsSent) {
        this.reminderStatsSent = reminderStatsSent;
    }

    public boolean isReminder3hSent() {
        return reminder3hSent;
    }

    public void setReminder3hSent(boolean reminder3hSent) {
        this.reminder3hSent = reminder3hSent;
    }

    public boolean isReminderAfterSent() {
        return reminderAfterSent;
    }

    public void setReminderAfterSent(boolean reminderAfterSent) {
        this.reminderAfterSent = reminderAfterSent;
    }
}
