package info.isaksson.erland.survey.auth;

import info.isaksson.erland.survey.auth.AuthService.AdminPrincipal;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class AdminRequestContext {
    private AdminPrincipal principal;

    public AdminPrincipal principal() {
        return principal;
    }

    public void setPrincipal(AdminPrincipal principal) {
        this.principal = principal;
    }
}
