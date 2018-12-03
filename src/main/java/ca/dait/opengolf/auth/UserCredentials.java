package ca.dait.opengolf.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class UserCredentials implements Authentication {

    private final String principal;
    private final Collection<GrantedAuthority> authorities;
    private transient boolean authenticated = false;

    public UserCredentials(boolean authenticated, String principal, String authorities[]){
        this.authenticated = authenticated;
        this.principal = principal;
        this.authorities = Arrays.stream(authorities)
                                    .map((authority) -> new SimpleGrantedAuthority(authority.toUpperCase()))
                                    .collect(Collectors.toSet());
    }

    @Override
    public String getPrincipal() {
        return this.principal;
    }

    @Override
    public String getName() {
        return this.principal;
    }

    @Override
    public String getCredentials() {
        return null;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean authenticated) throws IllegalArgumentException {
        this.authenticated = authenticated;
    }
}
