-- События интеграции: отправка сообщений в Telegram, доставка, ошибки
CREATE TABLE integration_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    target_chat_id VARCHAR(50),
    success BOOLEAN NOT NULL,
    error_message TEXT,
    team_id BIGINT REFERENCES teams(id) ON DELETE SET NULL,
    match_id BIGINT REFERENCES matches(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_integration_event_created_at ON integration_event(created_at DESC);
CREATE INDEX idx_integration_event_type ON integration_event(event_type);
CREATE INDEX idx_integration_event_success ON integration_event(success);
