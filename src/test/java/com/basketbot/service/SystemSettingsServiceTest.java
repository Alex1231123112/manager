package com.basketbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Автотесты по сценариям smoke: подготовка и создание команды (P1, 1.1, 1.3).
 * Покрывают «Доступ в бот» (admin @username) и проверку canCreateTeamWithoutInvite.
 */
@SpringBootTest
@ActiveProfiles("test")
class SystemSettingsServiceTest {

    @Autowired
    private SystemSettingsService systemSettingsService;

    @BeforeEach
    void setUp() {
        systemSettingsService.setAdminTelegramId("");
        systemSettingsService.setAdminTelegramUsername("");
    }

    @Test
    void canCreateTeamWithoutInvite_byTelegramId_whenAdminIdSet_returnsTrue() {
        systemSettingsService.setAdminTelegramId("12345");

        assertThat(systemSettingsService.canCreateTeamWithoutInvite("12345", null)).isTrue();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite("12345", "other")).isTrue();
    }

    @Test
    void canCreateTeamWithoutInvite_byTelegramId_whenNoMatch_returnsFalse() {
        systemSettingsService.setAdminTelegramId("12345");

        assertThat(systemSettingsService.canCreateTeamWithoutInvite("99999", null)).isFalse();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, "someuser")).isFalse();
    }

    @Test
    void canCreateTeamWithoutInvite_byUsername_whenAdminUsernameSet_returnsTrue() {
        systemSettingsService.setAdminTelegramUsername("adminuser");

        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, "adminuser")).isTrue();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, "@adminuser")).isTrue();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite("any", "adminuser")).isTrue();
    }

    @Test
    void canCreateTeamWithoutInvite_byUsername_caseInsensitive() {
        systemSettingsService.setAdminTelegramUsername("AdminUser");

        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, "adminuser")).isTrue();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, "ADMINUSER")).isTrue();
    }

    @Test
    void canCreateTeamWithoutInvite_whenBothEmpty_returnsFalse() {
        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, null)).isFalse();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite("", "")).isFalse();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, "someone")).isFalse();
        assertThat(systemSettingsService.canCreateTeamWithoutInvite("123", null)).isFalse();
    }

    @Test
    void canCreateTeamWithoutInvite_whenAdminUsernameEmpty_noMatchByUsername() {
        systemSettingsService.setAdminTelegramId("");
        systemSettingsService.setAdminTelegramUsername("");

        assertThat(systemSettingsService.canCreateTeamWithoutInvite(null, "user")).isFalse();
    }

    @Test
    void getAndSetAdminTelegramUsername_persists() {
        assertThat(systemSettingsService.getAdminTelegramUsername()).isEmpty();

        systemSettingsService.setAdminTelegramUsername("testuser");
        assertThat(systemSettingsService.getAdminTelegramUsername()).isEqualTo("testuser");

        systemSettingsService.setAdminTelegramUsername("@withAt");
        assertThat(systemSettingsService.getAdminTelegramUsername()).isEqualTo("withAt");
    }

    @Test
    void getAndSetAdminTelegramId_persists() {
        assertThat(systemSettingsService.getAdminTelegramId()).isEmpty();

        systemSettingsService.setAdminTelegramId("98765");
        assertThat(systemSettingsService.getAdminTelegramId()).isEqualTo("98765");
    }
}
