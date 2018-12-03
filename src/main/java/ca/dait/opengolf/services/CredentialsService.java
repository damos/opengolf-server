package ca.dait.opengolf.services;

import ca.dait.opengolf.auth.AuthenticationTokens;
import ca.dait.opengolf.auth.UserCredentials;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CredentialsService {

    private static final String ID_COOKIE_NAME = "s";
    private static final String REFRESH_COOKIE_NAME = "rt";
    private static final String COOKIE_PATH = "/";
    private static final boolean COOKIE_HTTP_ONLY = true;
    private static final int SESSION_COOKIE_MAX_AGE = -1;

    private final boolean cookieIsSecure;
    private final int cookieRefreshTimeout;

    private final UrlJwkProvider jwkProvider;
    private final Map<String, Algorithm> algorithms;

    @Autowired
    public CredentialsService(@Value("${ENV_JWK_URL}") String jwkUrl,
                              @Value("${ENV_AUTH_COOKIE_SECURE:false}") boolean cookieIsSecure,
                              @Value("${ENV_REFRESH_COOKIE_TIMEOUT:2592000}") int cookieRefreshTimeout) throws MalformedURLException{

        this.jwkProvider = new UrlJwkProvider(new URL(jwkUrl));
        this.algorithms = new HashMap<>();

        this.cookieIsSecure = cookieIsSecure;
        this.cookieRefreshTimeout = cookieRefreshTimeout;
    }

    public UserCredentials verifyCredentials(HttpServletRequest request, HttpServletResponse response){
        AuthenticationTokens tokens = getAuthenticationTokens(request);
        if(tokens.id != null){
            try {
                DecodedJWT decodedJWT = JWT.decode(tokens.id);
                this.verifyJWT(decodedJWT);

                Claim expires = decodedJWT.getClaim("exp");
                if(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
                        < expires.asLong().longValue()) {

                    Claim username = decodedJWT.getClaim("cognito:username");
                    Claim roles = decodedJWT.getClaim("cognito:groups");
                    return new UserCredentials(true, username.as(String.class), roles.asArray(String.class));
                }
            }
            catch(JwkException e){
                e.printStackTrace(System.err);
                //TODO: Add logging
            }
            //Unable to find valid credentials in the request, clearing id cookie.
            this.clearCookie(ID_COOKIE_NAME, response);
        }
       // JWT.decode()
        return null;
    }

    public void save(AuthenticationTokens tokens, HttpServletResponse response){
        Cookie idCookie = new Cookie(ID_COOKIE_NAME, tokens.id);
        idCookie.setSecure(this.cookieIsSecure);
        idCookie.setHttpOnly(COOKIE_HTTP_ONLY);
        idCookie.setMaxAge(SESSION_COOKIE_MAX_AGE); //TODO: Set this based on token timeout?
        idCookie.setPath(COOKIE_PATH);
        response.addCookie(idCookie);

        Cookie refreshCookie = new Cookie(REFRESH_COOKIE_NAME, tokens.refresh);
        refreshCookie.setSecure(this.cookieIsSecure);
        refreshCookie.setHttpOnly(COOKIE_HTTP_ONLY);
        refreshCookie.setMaxAge(this.cookieRefreshTimeout); //30 Days
        refreshCookie.setPath(COOKIE_PATH);
        response.addCookie(refreshCookie);

    }

    public void clear(HttpServletResponse response){
        this.clearCookie(ID_COOKIE_NAME, response);
        this.clearCookie(REFRESH_COOKIE_NAME, response);
    }

    private void clearCookie(String cookieName, HttpServletResponse response){
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setSecure(this.cookieIsSecure);
        cookie.setHttpOnly(COOKIE_HTTP_ONLY);
        cookie.setMaxAge(0);
        cookie.setPath(COOKIE_PATH);
        response.addCookie(cookie);
    }

    private void verifyJWT(DecodedJWT decodedJWT) throws JwkException{
        String keyId = decodedJWT.getKeyId();
        Algorithm algorithm = this.algorithms.get(keyId);
        if (algorithm == null) {
            if ("RS256".equals(decodedJWT.getAlgorithm())) {
                Jwk jwk = this.jwkProvider.get(keyId);
                algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
                this.algorithms.put(keyId, algorithm);
            } else {
                throw new IllegalArgumentException(decodedJWT.getAlgorithm());
            }
        }
        algorithm.verify(decodedJWT);
    }

    private AuthenticationTokens getAuthenticationTokens(HttpServletRequest request){
        return new AuthenticationTokens(this.getCookieValue(ID_COOKIE_NAME, request),
                this.getCookieValue(REFRESH_COOKIE_NAME, request));
    }

    private String getCookieValue(String name, HttpServletRequest request){
        Cookie cookies[] = request.getCookies();
        if(cookies != null){
            for(Cookie cookie : request.getCookies()){
                //Make sure the cookie doesn't match the DEL value.
                if(name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

}
