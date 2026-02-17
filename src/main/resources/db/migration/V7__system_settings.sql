-- Глобальные настройки приложения (ключ-значение)
CREATE TABLE system_settings (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT
);

-- Ключ для Telegram ID администратора (кто может создавать команду в боте без приглашения)
INSERT INTO system_settings (key, value) VALUES ('admin_telegram_id', '')
ON CONFLICT (key) DO NOTHING;
