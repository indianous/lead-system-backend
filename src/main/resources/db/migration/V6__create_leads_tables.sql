CREATE TABLE lead_origins (
    id UUID PRIMARY KEY,
    origin_type VARCHAR(20) NOT NULL,
    channel VARCHAR(30),
    search_source VARCHAR(30),
    region VARCHAR(255),
    search_segment VARCHAR(255),
    capture_method VARCHAR(20) NOT NULL
);

CREATE TABLE leads (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    lead_type VARCHAR(20) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(255),
    initial_message VARCHAR(2000),
    estimated_budget_cents INTEGER,
    desired_timeline VARCHAR(255),
    qualification_score VARCHAR(10),
    funnel_status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    loss_reason VARCHAR(1000),
    origin_id UUID NOT NULL REFERENCES lead_origins (id),
    assigned_user_id UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE lead_products (
    lead_id UUID NOT NULL REFERENCES leads (id),
    product_id UUID NOT NULL REFERENCES products (id),
    PRIMARY KEY (lead_id, product_id)
);
