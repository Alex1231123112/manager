-- Подтверждения участия в событии (матч): кто едет / опоздаю / не смогу
CREATE TABLE event_attendance (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    telegram_user_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(match_id, telegram_user_id)
);

CREATE INDEX idx_event_attendance_match_id ON event_attendance(match_id);
