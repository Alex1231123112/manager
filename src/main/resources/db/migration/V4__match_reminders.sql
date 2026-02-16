-- Флаги отправки напоминаний по матчам (Фаза 3)
ALTER TABLE matches ADD COLUMN IF NOT EXISTS reminder_24h_sent BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS reminder_3h_sent BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS reminder_after_sent BOOLEAN NOT NULL DEFAULT false;
