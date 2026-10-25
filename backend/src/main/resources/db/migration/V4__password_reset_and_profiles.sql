ALTER TABLE users ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE password_reset_tokens (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE report_profile_counts (
 report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
 profile_code VARCHAR(50) NOT NULL,
 profile_count INTEGER NOT NULL CHECK (profile_count >= 0 AND profile_count <= 1000000),
 PRIMARY KEY(report_id, profile_code)
);
