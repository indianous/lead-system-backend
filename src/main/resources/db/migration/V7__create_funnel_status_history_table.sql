CREATE TABLE funnel_status_histories (
    id UUID PRIMARY KEY,
    lead_id UUID NOT NULL REFERENCES leads (id),
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    user_id UUID NOT NULL REFERENCES users (id),
    reason VARCHAR(1000),
    changed_at TIMESTAMPTZ NOT NULL
);
