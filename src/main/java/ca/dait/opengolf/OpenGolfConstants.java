package ca.dait.opengolf;

public final class OpenGolfConstants {
    private OpenGolfConstants(){}

    public class API{
        public static final String CONTEXT_ROOT = "/api";
    }
    public class Auth{
        public static final String IS_AUTHENTICATED = "isAuthenticated()";
        public static final String IS_CONTRIBUTOR = "hasAuthority('CONTRIBUTOR')";
    }
}
