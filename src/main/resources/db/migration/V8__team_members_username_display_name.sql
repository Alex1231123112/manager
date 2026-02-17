-- Имя в Telegram (@username) и отображаемое имя для участников
ALTER TABLE team_members ADD COLUMN IF NOT EXISTS telegram_username VARCHAR(100);
ALTER TABLE team_members ADD COLUMN IF NOT EXISTS display_name VARCHAR(200);

-- Настройка доступа в бот по @username (вместо или вместе с ID)
INSERT INTO system_settings (key, value) VALUES ('admin_telegram_username', '')
ON CONFLICT (key) DO NOTHING;
