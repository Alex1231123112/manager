-- Участники команды и роли (админ, капитан, игрок)
CREATE TABLE team_members (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    telegram_user_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    UNIQUE(team_id, telegram_user_id)
);

CREATE INDEX idx_team_members_team_id ON team_members(team_id);
CREATE INDEX idx_team_members_telegram_user_id ON team_members(telegram_user_id);
