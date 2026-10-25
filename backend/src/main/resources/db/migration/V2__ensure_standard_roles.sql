-- Compatible aussi avec une base du prototype baselinée à V1.
INSERT INTO roles (name) SELECT 'COMMERCIAL' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'COMMERCIAL');
INSERT INTO roles (name) SELECT 'ADMIN' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');
INSERT INTO roles (name) SELECT 'SUPER_ADMIN' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SUPER_ADMIN');
