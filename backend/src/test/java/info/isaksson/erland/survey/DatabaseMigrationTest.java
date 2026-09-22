package info.isaksson.erland.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
