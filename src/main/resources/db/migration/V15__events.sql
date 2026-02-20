-- События команды (тренировки и т.п.), отдельно от матчей
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    event_type VARCHAR(20) NOT NULL DEFAULT 'TRAINING',
    event_date TIMESTAMP NOT NULL,
    location VARCHAR(200),
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_events_team_id ON events(team_id);
CREATE INDEX idx_events_event_date ON events(event_date);
