CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,
                       public_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_public_id ON users (public_id);