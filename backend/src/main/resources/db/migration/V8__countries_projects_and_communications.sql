CREATE TABLE sales_countries (code VARCHAR(2) PRIMARY KEY, name VARCHAR(100) NOT NULL);
INSERT INTO sales_countries(code,name) VALUES ('BJ','Bénin'),('NG','Nigeria'),('TG','Togo'),('GH','Ghana'),('CI','Côte d’Ivoire'),('BF','Burkina Faso'),('SN','Sénégal');
ALTER TABLE users ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'BJ' REFERENCES sales_countries(code);
ALTER TABLE users ADD COLUMN director_country VARCHAR(2) REFERENCES sales_countries(code);
ALTER TABLE users ADD COLUMN zone_level VARCHAR(20);
CREATE UNIQUE INDEX ux_director_country ON users(director_country);
CREATE TABLE director_projects(user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, project_code VARCHAR(20) NOT NULL, PRIMARY KEY(user_id,project_code));
INSERT INTO director_projects(user_id,project_code) SELECT DISTINCT ur.user_id,'AZO' FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.name IN ('DIRECTEUR_COMMERCIAL','ROLE_DIRECTEUR_COMMERCIAL');
INSERT INTO director_projects(user_id,project_code) SELECT DISTINCT ur.user_id,'ZEHAMI' FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.name IN ('DIRECTEUR_COMMERCIAL','ROLE_DIRECTEUR_COMMERCIAL');
-- Only infer the country if there is exactly one existing director. Otherwise the root must configure each account.
UPDATE users SET director_country='BJ' WHERE id IN (SELECT user_id FROM director_projects) AND (SELECT COUNT(DISTINCT user_id) FROM director_projects)=1;
ALTER TABLE reports ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'BJ';
ALTER TABLE supervisor_summaries ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'BJ';
CREATE TABLE activity_events(id BIGSERIAL PRIMARY KEY, actor_id BIGINT NOT NULL REFERENCES users(id), actor_name VARCHAR(210) NOT NULL, action VARCHAR(50) NOT NULL, message VARCHAR(1000) NOT NULL, country_code VARCHAR(2), project_code VARCHAR(20), created_at TIMESTAMP NOT NULL);
CREATE INDEX ix_activity_scope ON activity_events(country_code,project_code,id);
CREATE TABLE announcements(id BIGSERIAL PRIMARY KEY, author_id BIGINT NOT NULL REFERENCES users(id), author_name VARCHAR(210) NOT NULL, title VARCHAR(160) NOT NULL, content VARCHAR(8000) NOT NULL, country_code VARCHAR(2), created_at TIMESTAMP NOT NULL);
CREATE TABLE announcement_projects(announcement_id BIGINT NOT NULL REFERENCES announcements(id) ON DELETE CASCADE, project_code VARCHAR(20) NOT NULL, PRIMARY KEY(announcement_id,project_code));
