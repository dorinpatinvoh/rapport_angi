INSERT INTO roles(name) VALUES ('DIRECTEUR_COMMERCIAL'), ('SUPERVISEUR');
ALTER TABLE users ADD COLUMN supervisor_id BIGINT REFERENCES users(id);
ALTER TABLE users ADD COLUMN zone VARCHAR(150);
ALTER TABLE reports ADD COLUMN supervisor_id BIGINT REFERENCES users(id);
ALTER TABLE reports ADD COLUMN zone VARCHAR(150);
CREATE INDEX ix_users_supervisor ON users(supervisor_id);
CREATE INDEX ix_reports_supervisor_date ON reports(supervisor_id, report_date);
CREATE TABLE report_goal_counts (
 report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
 goal_code VARCHAR(50) NOT NULL,
 goal_count INTEGER NOT NULL CHECK (goal_count BETWEEN 0 AND 1000000),
 PRIMARY KEY(report_id, goal_code)
);
CREATE TABLE supervisor_summaries (
 id BIGSERIAL PRIMARY KEY,
 supervisor_id BIGINT NOT NULL REFERENCES users(id),
 report_date DATE NOT NULL,
 team VARCHAR(20) NOT NULL,
 zone VARCHAR(150) NOT NULL,
 content TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL,
 UNIQUE(supervisor_id, report_date, team)
);
CREATE TABLE summary_reports (
 summary_id BIGINT NOT NULL REFERENCES supervisor_summaries(id) ON DELETE CASCADE,
 report_id BIGINT NOT NULL REFERENCES reports(id),
 PRIMARY KEY(summary_id, report_id)
);
