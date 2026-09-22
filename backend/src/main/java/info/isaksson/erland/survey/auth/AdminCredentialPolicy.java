package info.isaksson.erland.survey.auth;

import info.isaksson.erland.survey.surveyapi.ApiException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.regex.Pattern;

@ApplicationScoped
public class AdminCredentialPolicy {
    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public String normalizeNewAdminEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "INVALID_ADMIN_EMAIL", "Administratörens e-postadress måste anges.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 200 || !EMAIL.matcher(normalized).matches()) {
            throw new ApiException(400, "INVALID_ADMIN_EMAIL", "En giltig e-postadress måste anges för en ny administratör.");
        }
        return normalized;
    }

    public void requireValidPassword(String password) {
        if (password == null || password.isBlank() || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException(400, "ADMIN_PASSWORD_REQUIRED",
                    "Lösenordet måste vara minst " + MIN_PASSWORD_LENGTH + " tecken.");
        }
    }
}
