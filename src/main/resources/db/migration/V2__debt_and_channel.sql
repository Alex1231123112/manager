-- Долг игрока (для финансового учёта, Фаза 3)
ALTER TABLE players ADD COLUMN IF NOT EXISTS debt DECIMAL(10,2) NOT NULL DEFAULT 0;

-- ID чата канала для публикации постов (опционально, Фаза 2)
ALTER TABLE teams ADD COLUMN IF NOT EXISTS channel_telegram_chat_id VARCHAR(100);
