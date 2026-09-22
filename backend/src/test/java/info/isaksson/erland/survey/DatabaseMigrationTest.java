package info.isaksson.erland.survey;

import static org.junit.jupiter.api.Assertions.*;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DatabaseMigrationTest {

    @Inject
    AgroalDataSource dataSource;

    @Test
    void flywayBaselineMigrationHasRun() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT metadata_value FROM app_metadata WHERE metadata_key = ?")) {
            statement.setString(1, "schema.version");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertEquals("1", resultSet.getString(1));
            }
        }
    }

    @Test
    void surveyAccountMigrationHasRun() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(tableExists(connection, "survey_account"));
            assertTrue(tableExists(connection, "survey_account_admin"));
            assertTrue(columnExists(connection, "admin_user", "system_admin"));
            assertTrue(columnExists(connection, "admin_user", "active"));
            assertTrue(columnExists(connection, "survey", "survey_account_id"));
            assertTrue(columnExists(connection, "survey", "created_by_admin_user_id"));
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, "public." + table);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                )
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
