package ca.dait.opengolf.auth;

import ca.dait.opengolf.services.CredentialsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class StatelessAuthenticationFilter extends GenericFilterBean {

    private final CredentialsService credentialsService;

    public StatelessAuthenticationFilter(CredentialsService credentialsService){
        this.credentialsService = credentialsService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        Authentication authentication = this.credentialsService.verifyCredentials(
                                                    (HttpServletRequest) request, (HttpServletResponse) response);
        if(authentication != null){
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

}
