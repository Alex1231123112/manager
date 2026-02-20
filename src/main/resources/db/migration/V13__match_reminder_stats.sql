-- Время отправки 24h напоминания и флаг отправки статистики подтверждений
ALTER TABLE matches ADD COLUMN IF NOT EXISTS reminder_24h_sent_at TIMESTAMP;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS reminder_stats_sent BOOLEAN NOT NULL DEFAULT false;
