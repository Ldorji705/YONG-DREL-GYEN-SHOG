package com.yongdrel.authservice.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class AuthUuidMigrationIntegrationTest {

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("yongdrel_auth")
                    .withCommand("--log-bin-trust-function-creators=1");

    @BeforeEach
    void createLegacySchema() throws SQLException {
        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS "
                    + "auth_users_identity_map_after_insert");
            statement.execute("DROP TABLE IF EXISTS auth_user_identity_map");
            statement.execute("DROP TABLE IF EXISTS auth_users");
            statement.execute("""
                    CREATE TABLE auth_users (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        role VARCHAR(255) NOT NULL,
                        PRIMARY KEY (id)
                    ) ENGINE = InnoDB
                    """);

            for (int id = 1; id <= 9; id++) {
                statement.execute("""
                        INSERT INTO auth_users
                            (email, password_hash, role)
                        VALUES ('legacy-%d@example.com', 'hash-%d', '%s')
                        """.formatted(
                        id,
                        id,
                        id == 9 ? "ORGANIZER" : "USER"
                ));
            }
        }
    }

    @Test
    void appliesMigrationAndPreservesLegacyDataAndMapping()
            throws Exception {
        executeScript(
                "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
        );

        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            assertEquals(9, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users"));
            assertEquals(9, scalar(statement,
                    "SELECT COUNT(*) FROM auth_user_identity_map"));
            assertEquals(9, scalar(statement,
                    "SELECT COUNT(DISTINCT email) FROM auth_users"));
            assertEquals(8, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users WHERE role = 'USER'"));
            assertEquals(1, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users "
                            + "WHERE role = 'ORGANIZER'"));
            assertEquals(0, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users "
                            + "WHERE password_hash NOT LIKE 'hash-%'"));
            assertEquals("binary(16)", columnType(statement, "id"));
            assertEquals(9, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users u "
                            + "JOIN auth_user_identity_map m "
                            + "ON m.legacy_id = u.legacy_id "
                            + "AND m.user_uuid = u.id"));
        }
    }

    @Test
    void rollsBackToNumericIdentityWithoutDeletingUsers()
            throws Exception {
        executeScript(
                "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
        );
        insertNewUserThroughMigratedSchema();
        executeScript(
                "/db/migration/manual/V2__auth_uuid_identity_rollback.sql"
        );

        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            assertEquals(10, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users"));
            assertEquals(
                    "bigint",
                    columnType(statement, "id"),
                    columns(statement)
            );
            assertEquals(10, scalar(statement,
                    "SELECT COUNT(*) FROM auth_user_identity_map"));
            assertEquals(10, scalar(statement,
                    "SELECT COUNT(DISTINCT id) FROM auth_users"));
        }
    }

    @Test
    void migratedSchemaSupportsNewUuidPersistenceAndDuplicateEmailProtection()
            throws Exception {
        executeScript(
                "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
        );

        UUID newUserId = insertNewUserThroughMigratedSchema();

        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            assertEquals(10, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users"));
            assertEquals(10, scalar(statement,
                    "SELECT COUNT(*) FROM auth_user_identity_map"));
            assertEquals(1, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users "
                            + "WHERE id = UUID_TO_BIN('"
                            + newUserId + "')"));
            assertEquals(1, scalar(statement,
                    "SELECT COUNT(*) FROM auth_user_identity_map "
                            + "WHERE legacy_id = 10"));
            assertEquals(1, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users "
                            + "WHERE email = 'new-user@example.com' "
                            + "AND role = 'USER'"));
            assertEquals("binary(16)", columnType(statement, "id"));
            assertEquals("bigint", columnType(statement, "legacy_id"));
        }

        assertDuplicateEmailRejected();
    }

    @Test
    void duplicateMigrationIsRejectedWithoutChangingMigratedSchema()
            throws Exception {
        executeScript(
                "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
        );

        org.junit.jupiter.api.Assertions.assertThrows(
                SQLException.class,
                () -> executeScript(
                        "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
                )
        );

        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            assertEquals(9, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users"));
            assertEquals("binary(16)", columnType(statement, "id"));
        }
    }

    @Test
    void unexpectedLegacyCountIsRejectedBeforeSchemaChanges()
            throws Exception {
        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM auth_users WHERE id = 9");
        }

        org.junit.jupiter.api.Assertions.assertThrows(
                SQLException.class,
                () -> executeScript(
                        "/db/migration/manual/V2__auth_uuid_identity_apply.sql"
                )
        );

        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            assertEquals(8, scalar(statement,
                    "SELECT COUNT(*) FROM auth_users"));
            assertEquals("bigint", columnType(statement, "id"));
            assertEquals(0, scalar(statement,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = 'auth_user_identity_map'"));
        }
    }

    private UUID insertNewUserThroughMigratedSchema() throws Exception {
        Properties properties = new Properties();
        properties.put("hibernate.connection.url", mysql.getJdbcUrl());
        properties.put("hibernate.connection.username", mysql.getUsername());
        properties.put("hibernate.connection.password", mysql.getPassword());
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.show_sql", "false");
        properties.put(
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        );

        try (SessionFactory sessionFactory = new Configuration()
                .addAnnotatedClass(AuthUser.class)
                .addProperties(properties)
                .buildSessionFactory();
             Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            AuthUser user = new AuthUser();
            user.setEmail("new-user@example.com");
            user.setPasswordHash("new-user-hash");
            user.setRole(UserRole.USER);
            session.persist(user);
            session.getTransaction().commit();
            assertTrue(user.getId() != null);
            return user.getId();
        }
    }

    private void assertDuplicateEmailRejected() throws SQLException {
        try (Connection connection = mysql.createConnection("");
             Statement statement = connection.createStatement()) {
            org.junit.jupiter.api.Assertions.assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate("""
                            INSERT INTO auth_users
                                (email, password_hash, role)
                            VALUES ('new-user@example.com',
                                    'different-hash', 'USER')
                            """)
            );
        }
    }

    private void executeScript(String resource) throws Exception {
        String script;
        try (var stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            script = new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Connection connection = mysql.createConnection("");
             Statement jdbc = connection.createStatement()) {
            for (String line : script.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("DELIMITER ")) {
                    delimiter = trimmed.substring("DELIMITER ".length());
                    continue;
                }

                statement.append(line).append('\n');
                if (trimmed.endsWith(delimiter)) {
                    String sql = statement
                            .toString()
                            .trim();
                    sql = sql.substring(
                            0,
                            sql.length() - delimiter.length()
                    ).trim();
                    if (!sql.isBlank()) {
                        jdbc.execute(sql);
                    }
                    statement.setLength(0);
                }
            }
        }
    }

    private int scalar(Statement statement, String sql)
            throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String columnType(Statement statement, String column)
            throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "SELECT column_type FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'auth_users' "
                        + "AND column_name = '" + column + "'")) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private String columns(Statement statement) throws SQLException {
        StringBuilder result = new StringBuilder();
        try (ResultSet rows = statement.executeQuery(
                "SELECT column_name, column_type "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'auth_users' "
                        + "ORDER BY ordinal_position")) {
            while (rows.next()) {
                result.append(rows.getString(1))
                        .append('=')
                        .append(rows.getString(2))
                        .append(' ');
            }
        }
        return result.toString();
    }
}
