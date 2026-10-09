CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN','USER')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE api_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(128) NOT NULL,
    key_hash     VARCHAR(64)  NOT NULL UNIQUE,
    key_prefix   VARCHAR(12)  NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ
);

CREATE TABLE credentials (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name              VARCHAR(128) NOT NULL,
    type              VARCHAR(32)  NOT NULL CHECK (type IN ('FORM_LOGIN','BASIC_AUTH','BEARER','COOKIE','CLIENT_CERT','PROXY')),
    payload_encrypted TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE scenarios (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE jobs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID REFERENCES users(id),
    scenario_type  VARCHAR(16) NOT NULL CHECK (scenario_type IN ('BDD','EXPLORATORY','MONKEY','AI_DRIVEN')),
    status         VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','CANCELLED')),
    target_url     TEXT  NOT NULL,
    request_params JSONB NOT NULL,
    credential_id  UUID REFERENCES credentials(id),
    scenario_id    UUID REFERENCES scenarios(id),
    callback_url   TEXT,
    error          TEXT,
    locked_by      VARCHAR(64),
    locked_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ
);
CREATE INDEX idx_jobs_pending ON jobs (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_jobs_user    ON jobs (user_id, created_at DESC);
CREATE INDEX idx_jobs_status  ON jobs (status);

CREATE TABLE reports (
    job_id     UUID PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE,
    summary    JSONB NOT NULL,
    steps      JSONB NOT NULL,
    artifacts  JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);