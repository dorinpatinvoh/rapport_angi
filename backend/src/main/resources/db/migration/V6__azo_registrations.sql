CREATE TABLE report_azo_registrations (
 report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
 row_index INTEGER NOT NULL,
 first_name VARCHAR(100) NOT NULL,
 last_name VARCHAR(100) NOT NULL,
 profile_code VARCHAR(50) NOT NULL,
 PRIMARY KEY(report_id, row_index)
);
