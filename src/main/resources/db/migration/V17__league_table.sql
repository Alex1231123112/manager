CREATE TABLE league_table_rows (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    position INTEGER NOT NULL DEFAULT 1,
    team_name VARCHAR(200) NOT NULL,
    wins INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0,
    points_diff INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_league_table_rows_team_id ON league_table_rows(team_id);
