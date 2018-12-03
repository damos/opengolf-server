package ca.dait.opengolf.controllers;

import ca.dait.opengolf.OpenGolfConstants;
import ca.dait.opengolf.auth.AuthenticationTokens;
import ca.dait.opengolf.services.CredentialsService;
import ca.dait.opengolf.services.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping(OpenGolfConstants.API.CONTEXT_ROOT + "/session")
public class SessionController {

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private LoginService cognitoLoginService;

    @RequestMapping(method = RequestMethod.POST)
    public void login(@RequestParam("username") String username,
                      @RequestParam("password") String password,
                      HttpServletResponse response) throws IOException {

        AuthenticationTokens tokens = this.cognitoLoginService.login(username, password);
        this.credentialsService.save(tokens, response);
    }

    @RequestMapping(method = RequestMethod.POST, path="confirm")
    public void confirm(@RequestParam("username") String username,
                        @RequestParam("tempPassword") String tempPassword,
                        @RequestParam("newPassword") String newPassword,
                        HttpServletResponse response) throws IOException {

        AuthenticationTokens tokens = this.cognitoLoginService.confirmRegistration(username, tempPassword, newPassword);
        this.credentialsService.save(tokens, response);
    }

    @PreAuthorize(OpenGolfConstants.Auth.IS_AUTHENTICATED)
    @RequestMapping(method = RequestMethod.DELETE)
    public void logoff(HttpServletResponse response) throws IOException {
        this.credentialsService.clear(response);
    }
}
