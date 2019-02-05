package ca.dait.opengolf.services;

import ca.dait.opengolf.auth.AuthenticationTokens;
import ca.dait.opengolf.auth.UserCredentials;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles persistence and verification of the users session. The session is comprised of an AWS ID and refresh token
 * saved as cookies.
 */
@Service
public class CredentialsService {

    private static final String ID_COOKIE_NAME = "s";
    private static final String REFRESH_COOKIE_NAME = "rt";
    private static final String COOKIE_PATH = "/";
    private static final boolean COOKIE_HTTP_ONLY = true;
    private static final int SESSION_COOKIE_MAX_AGE = -1;

    private static final String COGNITO_USERNAME = "cognito:username";
    private static final String COGNITO_GROUPS = "cognito:groups";
    private static final String COGNITO_EXPIRATION = "exp";

    @Autowired
    protected LoginService loginService;

    private final boolean cookieIsSecure;
    private final int cookieRefreshTimeout;

    private final UrlJwkProvider jwkProvider;
    private final Map<String, Algorithm> algorithms;

    private static final Logger LOGGER = Logger.getLogger(CredentialsService.class.getName());

    @Autowired
    public CredentialsService(@Value("${ENV_JWK_URL}") String jwkUrl,
                              @Value("${ENV_AUTH_COOKIE_SECURE:true}") boolean cookieIsSecure,
                              @Value("${ENV_REFRESH_COOKIE_TIMEOUT:2592000}") int cookieRefreshTimeout) throws MalformedURLException{

        this.jwkProvider = new UrlJwkProvider(new URL(jwkUrl));
        this.algorithms = new HashMap<>();
        this.cookieIsSecure = cookieIsSecure;
        this.cookieRefreshTimeout = cookieRefreshTimeout;
    }

    /**
     * Checks the HTTP request for valid session tokens to authorize the user.
     * - Check for a valid ID token. If valid return the UserCredentials instance.
     * - If no valid ID token exists, check for a refresh token.
     *      - If found try to received a new ID token.
     *
     * - If no valid tokens found, return null as the user is not authenticated.
     *
     * @param request
     * @param response
     * @return
     */
    public UserCredentials verifyCredentials(HttpServletRequest request, HttpServletResponse response){
        UserCredentials result = null;
        AuthenticationTokens tokens = getAuthenticationTokens(request);

        if(tokens.id != null){
            result = this.verifyIdToken(tokens.id);
            if(result == null){
                //Unable to find valid credentials in id token,clearing id cookie.
                this.clearCookie(ID_COOKIE_NAME, response);
            }
            else{
                return result;
            }
        }
        if(tokens.refresh != null){
            tokens = this.loginService.refreshTokens(tokens.refresh);
            if(tokens == null){
                //Unable to refresh auth tokens with the refresh token, clearing refresh cookie.
                this.clearCookie(REFRESH_COOKIE_NAME, response);
            }
            else{
                result = this.verifyIdToken(tokens.id);
                this.save(tokens, response);
            }
        }
        return result;
    }

    /**
     * Attempts to validate the ID JWT token. Return null if the token is invalid.
     *
     * @param token
     * @return
     */
    protected UserCredentials verifyIdToken(String token){
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            this.verifyJWT(decodedJWT);

            Claim expires = decodedJWT.getClaim(COGNITO_EXPIRATION);
            if(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) < expires.asLong()) {
                Claim username = decodedJWT.getClaim(COGNITO_USERNAME);
                Claim roles = decodedJWT.getClaim(COGNITO_GROUPS);
                return new UserCredentials(true, username.as(String.class), roles.asArray(String.class));
            }
        }
        catch(JwkException | JWTVerificationException e){
            LOGGER.log(Level.FINEST, "Failed to verify: " + token, e);
        }
        return null;
    }

    /**
     * Saves authentication tokens as cookies.
     *
     * @param tokens
     * @param response
     */
    public void save(AuthenticationTokens tokens, HttpServletResponse response) {
        if (tokens.id != null) {
            Cookie idCookie = new Cookie(ID_COOKIE_NAME, tokens.id);
            idCookie.setSecure(this.cookieIsSecure);
            idCookie.setHttpOnly(COOKIE_HTTP_ONLY);
            idCookie.setMaxAge(SESSION_COOKIE_MAX_AGE); //TODO: Set this based on token timeout?
            idCookie.setPath(COOKIE_PATH);
            response.addCookie(idCookie);
        }
        if (tokens.refresh != null){
            Cookie refreshCookie = new Cookie(REFRESH_COOKIE_NAME, tokens.refresh);
            refreshCookie.setSecure(this.cookieIsSecure);
            refreshCookie.setHttpOnly(COOKIE_HTTP_ONLY);
            refreshCookie.setMaxAge(this.cookieRefreshTimeout); //30 Days
            refreshCookie.setPath(COOKIE_PATH);
            response.addCookie(refreshCookie);
        }
    }

    /**
     * Clears authentication cookies
     *
     * @param response
     */
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

    /**
     * Verify the provided JWT token
     *
     * @param decodedJWT
     * @throws JwkException
     */
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

    /**
     * Retrieve the authentication tokens from the Request.
     *
     * @param request
     * @return
     */
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
