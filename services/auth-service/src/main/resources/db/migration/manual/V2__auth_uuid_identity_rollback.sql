-- Roll back the UUID primary-key cutover while preserving user rows.
-- Deploy the pre-migration application before executing this script.
-- The mapping table is retained for audit and future re-migration.

DELIMITER //

DROP PROCEDURE IF EXISTS auth_uuid_migration_rollback_preflight//
CREATE PROCEDURE auth_uuid_migration_rollback_preflight()
BEGIN
    IF DATABASE() <> 'yongdrel_auth' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Wrong database; expected yongdrel_auth';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'auth_user_identity_map'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Identity mapping table is missing';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'auth_users'
          AND column_name = 'id'
          AND column_type = 'binary(16)'
          AND column_key = 'PRI'
    ) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'auth_users is not in UUID-migrated state';
    END IF;
END//

CALL auth_uuid_migration_rollback_preflight()//
DROP PROCEDURE auth_uuid_migration_rollback_preflight//

DELIMITER ;

DROP TRIGGER IF EXISTS auth_users_identity_map_after_insert;

ALTER TABLE auth_users
    DROP PRIMARY KEY,
    DROP KEY uk_auth_users_uuid_id,
    DROP COLUMN id;

ALTER TABLE auth_users
    CHANGE COLUMN legacy_id id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE auth_users
    ADD PRIMARY KEY (id),
    DROP KEY uk_auth_users_legacy_id;
