CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    lead_id UUID NOT NULL REFERENCES leads (id),
    channel VARCHAR(20) NOT NULL,
    external_thread_id VARCHAR(255) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (channel, external_thread_id)
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations (id),
    direction VARCHAR(10) NOT NULL,
    sender_user_id UUID REFERENCES users (id),
    content TEXT NOT NULL,
    media_url VARCHAR(500),
    external_message_id VARCHAR(255),
    status VARCHAR(10) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL
);
