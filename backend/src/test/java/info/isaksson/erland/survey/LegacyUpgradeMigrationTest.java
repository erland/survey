package info.isaksson.erland.survey;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LegacyUpgradeMigrationTest {

    @Inject
    AgroalDataSource dataSource;

    @Test
    void versionSevenDataSurvivesMultiUserMigration() throws Exception {
        String schema = "migration_smoke_" + UUID.randomUUID().toString().replace("-", "");
        UUID adminId = UUID.randomUUID();
        UUID surveyId = UUID.randomUUID();

        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("7"))
                    .load()
                    .migrate();

            try (Connection connection = dataSource.getConnection()) {
                connection.setSchema(schema);
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO admin_user (id, username, password_hash)
                        VALUES (?, 'legacy-admin', 'legacy-password-hash')
                        """)) {
                    ps.setObject(1, adminId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO survey (id, owner_id, title, description)
                        VALUES (?, ?, 'Legacy survey', 'Must survive V8/V9')
                        """)) {
                    ps.setObject(1, surveyId);
                    ps.setObject(2, adminId);
                    ps.executeUpdate();
                }
            }

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = dataSource.getConnection()) {
                connection.setSchema(schema);

                assertFalse(columnExists(connection, schema, "survey", "owner_id"));
                assertTrue(columnExists(connection, schema, "survey", "survey_account_id"));
                assertEquals("Legacy survey", scalar(connection,
                        "SELECT title FROM survey WHERE id = ?", surveyId));
                assertEquals(adminId, scalar(connection,
                        "SELECT created_by_admin_user_id FROM survey WHERE id = ?", surveyId));

                UUID accountId = (UUID) scalar(connection,
                        "SELECT survey_account_id FROM survey WHERE id = ?", surveyId);
                assertNotNull(accountId);
                assertEquals(1L, ((Number) scalar(connection, """
                        SELECT COUNT(*)
                        FROM survey_account_admin
                        WHERE survey_account_id = ? AND admin_user_id = ?
                        """, accountId, adminId)).longValue());
            }
        } finally {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private boolean columnExists(Connection connection, String schema, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = ? AND column_name = ?
                )
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private Object scalar(Connection connection, String sql, Object... params) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getObject(1);
            }
        }
    }
}
