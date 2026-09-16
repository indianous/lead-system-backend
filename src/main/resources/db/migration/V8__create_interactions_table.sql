CREATE TABLE interactions (
    id UUID PRIMARY KEY,
    lead_id UUID NOT NULL REFERENCES leads (id),
    user_id UUID NOT NULL REFERENCES users (id),
    type VARCHAR(10) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
