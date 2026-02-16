-- Статусы игроков: активен, травма, отпуск, не оплатил (Фаза 3)
ALTER TABLE players ADD COLUMN IF NOT EXISTS player_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
