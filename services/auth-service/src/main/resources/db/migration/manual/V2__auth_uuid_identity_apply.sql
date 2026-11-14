-- Auth UUID identity migration.
-- Execute with the mysql client against the dedicated yongdrel_auth database.
-- This script is intentionally not auto-discovered by Hibernate or Flyway.

DELIMITER //

DROP PROCEDURE IF EXISTS auth_uuid_migration_preflight//
CREATE PROCEDURE auth_uuid_migration_preflight()
BEGIN
    IF DATABASE() <> 'yongdrel_auth' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Wrong database; expected yongdrel_auth';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'auth_users'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'auth_users table is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'auth_user_identity_map'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Identity mapping table already exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'auth_users'
          AND column_name IN ('legacy_id', 'uuid_id')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration appears partially applied';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'auth_users'
          AND column_name = 'id'
          AND data_type = 'bigint'
          AND column_key = 'PRI'
    ) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'auth_users.id is not the expected BIGINT primary key';
    END IF;

    IF (SELECT COUNT(*) FROM auth_users) <> 9 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Unexpected auth_users row count; stop and review';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM auth_users
        WHERE email IS NULL
           OR password_hash IS NULL
           OR role IS NULL
           OR role NOT IN ('USER', 'ORGANIZER')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid legacy user data; stop and review';
    END IF;

    IF (
        SELECT COUNT(*) FROM auth_users
    ) <> (
        SELECT COUNT(DISTINCT email) FROM auth_users
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate email data; stop and review';
    END IF;
END//

CALL auth_uuid_migration_preflight()//
DROP PROCEDURE auth_uuid_migration_preflight//

DELIMITER ;

CREATE TABLE auth_user_identity_map (
    legacy_id BIGINT NOT NULL,
    user_uuid BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (legacy_id),
    UNIQUE KEY uk_auth_user_identity_uuid (user_uuid)
) ENGINE = InnoDB;

ALTER TABLE auth_users
    CHANGE COLUMN id legacy_id BIGINT NOT NULL AUTO_INCREMENT,
    ADD COLUMN uuid_id BINARY(16) NULL,
    ADD UNIQUE KEY uk_auth_users_legacy_id (legacy_id);

UPDATE auth_users
SET uuid_id = UUID_TO_BIN(UUID())
WHERE uuid_id IS NULL;

ALTER TABLE auth_users
    MODIFY COLUMN uuid_id BINARY(16) NOT NULL,
    ADD UNIQUE KEY uk_auth_users_uuid_id (uuid_id);

INSERT INTO auth_user_identity_map (legacy_id, user_uuid)
SELECT legacy_id, uuid_id
FROM auth_users;

ALTER TABLE auth_users
    DROP PRIMARY KEY,
    CHANGE COLUMN uuid_id id BINARY(16) NOT NULL,
    ADD PRIMARY KEY (id);

DELIMITER //

CREATE TRIGGER auth_users_identity_map_after_insert
AFTER INSERT ON auth_users
FOR EACH ROW
BEGIN
    INSERT INTO auth_user_identity_map (legacy_id, user_uuid)
    VALUES (NEW.legacy_id, NEW.id)
    ON DUPLICATE KEY UPDATE user_uuid = NEW.id;
END//

DELIMITER ;
