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

import java.math.BigDecimal;

@Entity
@Table(name = "players")
public class Player {

    /** Статус игрока для фильтрации состава */
    public enum PlayerStatus {
        ACTIVE,   // активен
        INJURY,   // травма
        VACATION, // отпуск
        NOT_PAID  // не оплатил
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 100)
    private String name;

    private Integer number;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "telegram_id", unique = true, length = 100)
    private String telegramId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "player_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PlayerStatus playerStatus = PlayerStatus.ACTIVE;

    @Column(name = "debt", nullable = false, precision = 10, scale = 2)
    private BigDecimal debt = BigDecimal.ZERO;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(String telegramId) {
        this.telegramId = telegramId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BigDecimal getDebt() {
        return debt;
    }

    public void setDebt(BigDecimal debt) {
        this.debt = debt != null ? debt : BigDecimal.ZERO;
    }

    public PlayerStatus getPlayerStatus() {
        return playerStatus;
    }

    public void setPlayerStatus(PlayerStatus playerStatus) {
        this.playerStatus = playerStatus != null ? playerStatus : PlayerStatus.ACTIVE;
    }
}
