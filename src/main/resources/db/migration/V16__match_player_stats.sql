-- Статистика игрока в матче
CREATE TABLE match_player_stats (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    minutes INTEGER,
    points INTEGER NOT NULL DEFAULT 0,
    rebounds INTEGER NOT NULL DEFAULT 0,
    assists INTEGER NOT NULL DEFAULT 0,
    fouls INTEGER NOT NULL DEFAULT 0,
    plus_minus INTEGER,
    is_mvp BOOLEAN NOT NULL DEFAULT false,
    UNIQUE(match_id, player_id)
);

CREATE INDEX idx_match_player_stats_match_id ON match_player_stats(match_id);
CREATE INDEX idx_match_player_stats_player_id ON match_player_stats(player_id);
