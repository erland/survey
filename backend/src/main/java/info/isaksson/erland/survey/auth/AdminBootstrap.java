package info.isaksson.erland.survey.auth;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AdminBootstrap {
    @Inject AgroalDataSource dataSource;
    @Inject PasswordHasher passwordHasher;

    @ConfigProperty(name = "app.bootstrap-admin.username")
    Optional<String> username;

    @ConfigProperty(name = "app.bootstrap-admin.password")
    Optional<String> password;

    void onStart(@Observes StartupEvent event) {
        if (username.isEmpty() || password.isEmpty() || username.get().isBlank() || password.get().isBlank()) {
            return;
        }
        ensureSystemAdmin(username.get().trim(), password.get());
    }

    private void ensureSystemAdmin(String user, String clearPassword) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID userId = findUserId(connection, user).orElseGet(() -> insertUser(connection, user, clearPassword));
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE admin_user SET system_admin = TRUE, active = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                    update.setObject(1, userId);
                    update.executeUpdate();
                }

                UUID accountId = findInitialAccount(connection).orElseGet(() -> insertDefaultAccount(connection));
                try (PreparedStatement membership = connection.prepareStatement("""
                        INSERT INTO survey_account_admin (survey_account_id, admin_user_id, role)
                        VALUES (?, ?, 'ADMIN')
                        ON CONFLICT DO NOTHING
                        """)) {
                    membership.setObject(1, accountId);
                    membership.setObject(2, userId);
                    membership.executeUpdate();
                }
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not bootstrap system admin user", e);
        }
    }

    private Optional<UUID> findUserId(Connection connection, String user) throws SQLException {
        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT id FROM admin_user WHERE lower(username) = lower(?)")) {
            lookup.setString(1, user);
            try (ResultSet rs = lookup.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject("id", UUID.class)) : Optional.empty();
            }
        }
    }

    private UUID insertUser(Connection connection, String user, String clearPassword) {
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO admin_user (id, username, password_hash, system_admin, active)
                VALUES (?, ?, ?, TRUE, TRUE)
                """)) {
            insert.setObject(1, id);
            insert.setString(2, user);
            insert.setString(3, passwordHasher.hash(clearPassword));
            insert.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create bootstrap admin", e);
        }
    }

    private Optional<UUID> findInitialAccount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM survey_account ORDER BY created_at, id LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? Optional.of(rs.getObject("id", UUID.class)) : Optional.empty();
        }
    }

    private UUID insertDefaultAccount(Connection connection) {
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO survey_account (id, name) VALUES (?, 'Default')")) {
            insert.setObject(1, id);
            insert.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create default survey account", e);
        }
    }
}
