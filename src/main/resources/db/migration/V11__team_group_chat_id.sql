-- Групповой чат для опросов и уведомлений (если команда создана в личке)
ALTER TABLE teams ADD COLUMN IF NOT EXISTS group_telegram_chat_id VARCHAR(100);
