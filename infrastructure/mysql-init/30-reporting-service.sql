-- Runs only when the MySQL volume is created for the first time.
-- Reporting Service owns its own schema; the shared app user gets rights on it only.
CREATE DATABASE IF NOT EXISTS yongdrel_reporting CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON yongdrel_reporting.* TO 'yongdrel'@'%';
FLUSH PRIVILEGES;
