-- Runs only when the MySQL volume is created for the first time.
-- Audit Log Service owns its own schema; the shared app user gets rights on it only.
CREATE DATABASE IF NOT EXISTS yongdrel_audit CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON yongdrel_audit.* TO 'yongdrel'@'%';
FLUSH PRIVILEGES;
