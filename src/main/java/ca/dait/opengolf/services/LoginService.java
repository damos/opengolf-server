package ca.dait.opengolf.services;

import ca.dait.opengolf.auth.AuthenticationTokens;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import com.amazonaws.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class LoginService {

    private static final String USERNAME = "USERNAME";
    private static final String PASSWORD = "PASSWORD";
    private static final String NEW_PASSWORD = "NEW_PASSWORD";
    private static final String REFRESH_TOKEN = "REFRESH_TOKEN";

    private final String userPoolId;
    private final String userPoolClientId;

    private final AWSCognitoIdentityProvider cognitoClient;

    private static final Logger LOGGER = Logger.getLogger(LoginService.class.getName());

    @Autowired
    public LoginService(@Value("${ENV_USERPOOL_ID}") String userPoolId,
                        @Value("${ENV_USERPOOL_CLIENT_ID}") String userPoolClientId) {

        this.userPoolId = userPoolId;
        this.userPoolClientId = userPoolClientId;
        this.cognitoClient = AWSCognitoIdentityProviderClientBuilder.defaultClient();
    }

    /**
     * Attempts to login a user with the given username and password.
     *
     * @param username  Username
     * @param password  Password
     * @return          Users authentication tokens.
     */
    public AuthenticationTokens login(String username, String password){
        Map<String, String> authParams = new HashMap<>();
        authParams.put(USERNAME, username);
        authParams.put(PASSWORD, password);

        AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest();
        authRequest.withAuthParameters(authParams);
        authRequest.setUserPoolId(this.userPoolId);
        authRequest.setClientId(this.userPoolClientId);
        authRequest.withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH);

        AdminInitiateAuthResult result = this.cognitoClient.adminInitiateAuth(authRequest);

        if(StringUtils.isNullOrEmpty(result.getChallengeName())){
            AuthenticationResultType resultType = result.getAuthenticationResult();
            return new AuthenticationTokens(resultType.getIdToken(), resultType.getRefreshToken());
        }
        else if(ChallengeNameType.NEW_PASSWORD_REQUIRED.toString().equals(result.getChallengeName())){
            throw new UserNotConfirmedException("User not confirmed. Complete confirm flow.");
        }
        else{
            throw new UnsupportedUserStateException("User contains unsupported challenge name. Contact your administrator.");
        }
    }

    /**
     * Attempts to complete first login of a user a change their temporary password.
     *
     * @param username  Username
     * @param password  Password
     * @param newPassword  New Password
     * @return          Users authentication tokens.
     */
    public AuthenticationTokens confirmRegistration(String username, String password, String newPassword){
        Map<String, String> authParams = new HashMap<>();
        authParams.put(USERNAME, username);
        authParams.put(PASSWORD, password);

        AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest();
        authRequest.withAuthParameters(authParams);
        authRequest.setUserPoolId(this.userPoolId);
        authRequest.setClientId(this.userPoolClientId);
        authRequest.withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH);

        AdminInitiateAuthResult result = this.cognitoClient.adminInitiateAuth(authRequest);

        if(ChallengeNameType.NEW_PASSWORD_REQUIRED.toString().equals(result.getChallengeName())){
            authParams.put(NEW_PASSWORD, newPassword);
            AdminRespondToAuthChallengeRequest challengeRequest = new AdminRespondToAuthChallengeRequest()
                    .withChallengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                    .withChallengeResponses(authParams)
                    .withUserPoolId(this.userPoolId)
                    .withClientId(this.userPoolClientId)
                    .withSession(result.getSession());

            AdminRespondToAuthChallengeResult challengeResult = this.cognitoClient.adminRespondToAuthChallenge(challengeRequest);
            if(StringUtils.isNullOrEmpty(challengeResult.getChallengeName())){
                AuthenticationResultType resultType = challengeResult.getAuthenticationResult();
                return new AuthenticationTokens(resultType.getIdToken(), resultType.getRefreshToken());
            }
            else{
                throw new UserNotConfirmedException("Unable to confirm user. Contact your administrator.");
            }
        }
        else{
            throw new UnsupportedUserStateException("User is not in the correct state. Contact your administrator.");
        }
    }

    public AuthenticationTokens refreshTokens(String refreshToken){
        Map<String, String> authParams = new HashMap<>();
        authParams.put(REFRESH_TOKEN, refreshToken);

        AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest();
        authRequest.withAuthParameters(authParams);
        authRequest.setUserPoolId(this.userPoolId);
        authRequest.setClientId(this.userPoolClientId);
        authRequest.withAuthFlow(AuthFlowType.REFRESH_TOKEN_AUTH);

        AdminInitiateAuthResult result = this.cognitoClient.adminInitiateAuth(authRequest);

        if(StringUtils.isNullOrEmpty(result.getChallengeName())){
            AuthenticationResultType resultType = result.getAuthenticationResult();
            return new AuthenticationTokens(resultType.getIdToken(), resultType.getRefreshToken());
        }
        else{
            LOGGER.log(Level.FINEST, "Unable to refresh tokens: " + refreshToken);
            return null;
        }
    }
}
