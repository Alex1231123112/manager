package com.basketbot.service;

import com.basketbot.model.SystemSetting;
import com.basketbot.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {

    public static final String KEY_ADMIN_TELEGRAM_ID = "admin_telegram_id";
    public static final String KEY_ADMIN_TELEGRAM_USERNAME = "admin_telegram_username";

    private final SystemSettingRepository repository;

    public SystemSettingsService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String getAdminTelegramId() {
        return repository.findById(KEY_ADMIN_TELEGRAM_ID)
                .map(SystemSetting::getValue)
                .orElse("");
    }

    @Transactional
    public void setAdminTelegramId(String id) {
        String value = id != null ? id.trim() : "";
        SystemSetting setting = repository.findById(KEY_ADMIN_TELEGRAM_ID)
                .orElseGet(() -> {
                    SystemSetting s = new SystemSetting();
                    s.setKey(KEY_ADMIN_TELEGRAM_ID);
                    return s;
                });
        setting.setValue(value);
        repository.save(setting);
    }

    @Transactional(readOnly = true)
    public String getAdminTelegramUsername() {
        return repository.findById(KEY_ADMIN_TELEGRAM_USERNAME)
                .map(SystemSetting::getValue)
                .orElse("");
    }

    @Transactional
    public void setAdminTelegramUsername(String username) {
        String value = username != null ? username.trim().replaceFirst("^@", "") : "";
        SystemSetting setting = repository.findById(KEY_ADMIN_TELEGRAM_USERNAME)
                .orElseGet(() -> {
                    SystemSetting s = new SystemSetting();
                    s.setKey(KEY_ADMIN_TELEGRAM_USERNAME);
                    return s;
                });
        setting.setValue(value);
        repository.save(setting);
    }

    /** Разрешить создание команды без приглашения: по Telegram ID или по @username. Пусто = никто. */
    @Transactional(readOnly = true)
    public boolean canCreateTeamWithoutInvite(String telegramUserId, String telegramUsername) {
        if (telegramUserId == null && (telegramUsername == null || telegramUsername.isBlank())) return false;
        String adminId = getAdminTelegramId();
        if (adminId != null && !adminId.isBlank() && telegramUserId != null && !telegramUserId.isBlank()
                && adminId.trim().equals(telegramUserId.trim())) {
            return true;
        }
        String adminUsername = getAdminTelegramUsername();
        if (adminUsername == null || adminUsername.isBlank()) return false;
        String uname = telegramUsername != null ? telegramUsername.trim().replaceFirst("^@", "") : "";
        return adminUsername.equalsIgnoreCase(uname);
    }
}
