-- Приход/расход по команде
CREATE TABLE finance_entries (
    id BIGSERIAL PRIMARY KEY,
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    description VARCHAR(500),
    entry_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_finance_entries_team_id ON finance_entries(team_id);
CREATE INDEX idx_finance_entries_entry_date ON finance_entries(entry_date);
