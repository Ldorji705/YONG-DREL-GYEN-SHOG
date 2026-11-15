-- Runs only when the MySQL volume is created for the first time.
-- Fraud & Risk Service owns its own schema; the shared app user gets rights on it only.
CREATE DATABASE IF NOT EXISTS yongdrel_fraud CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON yongdrel_fraud.* TO 'yongdrel'@'%';
FLUSH PRIVILEGES;
