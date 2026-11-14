package com.yongdrel.authservice.user;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthUuidMigrationScriptTest {

    @Test
    void applyScriptPreservesLegacyIdsAndCreatesStableMapping() throws IOException {
        String script = read(
                "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
        );

        assertTrue(script.contains("auth_user_identity_map"));
        assertTrue(script.contains("CHANGE COLUMN id legacy_id"));
        assertTrue(script.contains("UUID_TO_BIN(UUID())"));
        assertTrue(script.contains("INSERT INTO auth_user_identity_map"));
        assertTrue(script.contains("uk_auth_user_identity_uuid"));
        assertTrue(script.contains("auth_users_identity_map_after_insert"));
    }

    @Test
    void scriptsFailClosedOnUnexpectedDatabaseOrData() throws IOException {
        String apply = read(
                "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
        );
        String verify = read(
                "/db/migration/manual/V2__auth_uuid_identity_verify.sql"
        );
        String rollback = read(
                "/db/migration/manual/V2__auth_uuid_identity_rollback.sql"
        );

        assertTrue(apply.contains("Wrong database; expected yongdrel_auth"));
        assertTrue(apply.contains("Unexpected auth_users row count"));
        assertTrue(apply.contains("Invalid legacy user data"));
        assertTrue(verify.contains("Identity mapping mismatch"));
        assertTrue(verify.contains("COUNT(*) FROM auth_user_identity_map"));
        assertTrue(!verify.contains("mapping table) <> 9"));
        assertTrue(rollback.contains("auth_users is not in UUID-migrated state"));
    }

    @Test
    void scriptsRetainPasswordAndRoleColumnsWithoutPrintingSensitiveValues()
            throws IOException {
        String apply = read(
                "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
        );
        String verify = read(
                "/db/migration/manual/V2__auth_uuid_identity_verify.sql"
        );

        assertTrue(apply.contains("password_hash"));
        assertTrue(apply.contains("role"));
        assertTrue(verify.contains("password_hash"));
        assertTrue(!verify.contains("SELECT password_hash"));
    }

    @Test
    void rollbackRetainsMappingAndLegacyIdentity() throws IOException {
        String script = read(
                "/db/migration/manual/V2__auth_uuid_identity_rollback.sql"
        );

        assertTrue(script.contains("mapping table is retained"));
        assertTrue(script.contains("CHANGE COLUMN legacy_id id"));
        assertTrue(script.contains("DROP COLUMN id"));
    }

    private String read(String resource) throws IOException {
        try (var stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing migration resource: " + resource);
            }
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
