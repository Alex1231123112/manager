-- Команды
CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    telegram_chat_id VARCHAR(100) UNIQUE,
    logo_url TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Игроки
CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    number INTEGER,
    photo_url TEXT,
    telegram_id VARCHAR(100) UNIQUE,
    is_active BOOLEAN DEFAULT true
);

CREATE INDEX idx_players_team_id ON players(team_id);

-- Матчи
CREATE TABLE matches (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    opponent VARCHAR(100) NOT NULL,
    date TIMESTAMP NOT NULL,
    our_score INTEGER,
    opponent_score INTEGER,
    location VARCHAR(200),
    status VARCHAR(20) DEFAULT 'SCHEDULED'
);

CREATE INDEX idx_matches_team_id ON matches(team_id);
CREATE INDEX idx_matches_date ON matches(date);
