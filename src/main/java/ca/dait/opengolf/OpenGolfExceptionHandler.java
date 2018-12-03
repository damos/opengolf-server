package ca.dait.opengolf;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cognitoidp.model.AWSCognitoIdentityProviderException;
import com.amazonaws.services.cognitoidp.model.NotAuthorizedException;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.google.common.collect.ImmutableMap;
import org.jboss.logging.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class OpenGolfExceptionHandler{

    private static final String MESSAGE_KEY = "message";

    private static final Logger logger = Logger.getLogger(OpenGolfExceptionHandler.class);

    //AWS Cognito login exceptions
    @ExceptionHandler({UserNotFoundException.class, NotAuthorizedException.class})
    protected ResponseEntity<Object> handleAuthFailed(AWSCognitoIdentityProviderException e) {
        return new ResponseEntity<>(this.createBody(e.getErrorMessage()), HttpStatus.UNAUTHORIZED);
    }

    //TODO: This might not make sense to make all these a 400's...
    @ExceptionHandler(AmazonServiceException.class)
    protected ResponseEntity<Object> handleAWS(AmazonServiceException e) {
        logger.debug(e);
        return new ResponseEntity<>(this.createBody(e.getErrorMessage()), HttpStatus.BAD_REQUEST);
    }

    private Map<String, String> createBody(String message){
        return ImmutableMap.of(MESSAGE_KEY, message);
    }
}