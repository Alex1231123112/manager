package com.basketbot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "telegram_chat_id", unique = true, length = 100)
    private String telegramChatId;

    @Column(name = "channel_telegram_chat_id", length = 100)
    private String channelTelegramChatId;

    @Column(name = "group_telegram_chat_id", length = 100)
    private String groupTelegramChatId;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public String getChannelTelegramChatId() {
        return channelTelegramChatId;
    }

    public void setChannelTelegramChatId(String channelTelegramChatId) {
        this.channelTelegramChatId = channelTelegramChatId;
    }

    public String getGroupTelegramChatId() {
        return groupTelegramChatId;
    }

    public void setGroupTelegramChatId(String groupTelegramChatId) {
        this.groupTelegramChatId = groupTelegramChatId;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
