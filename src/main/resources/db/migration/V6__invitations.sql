-- Приглашения в команду (одноразовая ссылка/код)
CREATE TABLE invitations (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    code VARCHAR(32) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invitations_team_id ON invitations(team_id);
CREATE INDEX idx_invitations_code ON invitations(code);
CREATE INDEX idx_invitations_expires_at ON invitations(expires_at);
