package edu.application;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import javax.inject.Provider;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

public class MySecurityContext implements SecurityContext {

    final private Principal principal;
    private final Provider<UriInfo> uriInfo;
    private final Set<String> roles;

    public MySecurityContext(final Provider<UriInfo> uriInfo, final Principal principal, final Set<String> roles) {
        Objects.requireNonNull(principal);

        this.principal = principal;
        this.uriInfo = uriInfo;
        this.roles = roles;//new HashSet<>(Arrays.asList((roles != null) ? roles : new String[]{}));
        System.out.println("MySecurityContext init");
    }

    @Override
    public Principal getUserPrincipal() {
        return this.principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        role = role != null ? role : "";
        return this.roles.contains(role);
    }

    @Override
    public boolean isSecure() {
        return "https".equals(uriInfo.get().getRequestUri().getScheme());
    }

    @Override
    public String getAuthenticationScheme() {
        return "Bearer";
    }
}
