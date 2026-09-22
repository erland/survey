package info.isaksson.erland.survey.auth;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        ensureAdmin(username.get().trim(), password.get());
    }

    private void ensureAdmin(String user, String clearPassword) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement lookup = connection.prepareStatement(
                    "SELECT 1 FROM admin_user WHERE lower(username) = lower(?)")) {
                lookup.setString(1, user);
                try (ResultSet rs = lookup.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO admin_user (id, username, password_hash)
                    VALUES (?, ?, ?)
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setString(2, user);
                insert.setString(3, passwordHasher.hash(clearPassword));
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not bootstrap admin user", e);
        }
    }
}
