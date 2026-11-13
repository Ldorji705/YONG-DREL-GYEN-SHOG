-- Safe post-migration verification. Execute with the mysql client.

DELIMITER //

DROP PROCEDURE IF EXISTS auth_uuid_migration_verify//
CREATE PROCEDURE auth_uuid_migration_verify()
BEGIN
    IF DATABASE() <> 'yongdrel_auth' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Wrong database; expected yongdrel_auth';
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
            SET MESSAGE_TEXT = 'UUID primary key is not installed';
    END IF;

    IF (SELECT COUNT(*) FROM auth_users) <> (
           SELECT COUNT(*) FROM auth_user_identity_map
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'User or identity mapping count mismatch';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM auth_users u
        LEFT JOIN auth_user_identity_map m
            ON m.legacy_id = u.legacy_id
           AND m.user_uuid = u.id
        WHERE m.legacy_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Identity mapping mismatch';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM auth_users
        WHERE email IS NULL
           OR password_hash IS NULL
           OR role NOT IN ('USER', 'ORGANIZER')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid migrated user data';
    END IF;
END//

CALL auth_uuid_migration_verify()//
DROP PROCEDURE auth_uuid_migration_verify//

DELIMITER ;

SELECT
    (SELECT COUNT(*) FROM auth_users) AS user_count,
    (SELECT COUNT(*) FROM auth_user_identity_map) AS mapping_count,
    (SELECT COUNT(DISTINCT email) FROM auth_users) AS distinct_email_count,
    (SELECT COUNT(*) FROM auth_users WHERE role = 'USER') AS user_role_count,
    (SELECT COUNT(*) FROM auth_users WHERE role = 'ORGANIZER')
        AS organizer_role_count;
