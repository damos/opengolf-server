package ca.dait.opengolf.auth;

public class AuthenticationTokens {
    public final String id;
    public final String refresh;

    public AuthenticationTokens(String id, String refresh){
        this.id = id;
        this.refresh = refresh;
    }
}
