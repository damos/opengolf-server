package ca.dait.opengolf;

import ca.dait.opengolf.auth.StatelessAuthenticationFilter;
import ca.dait.opengolf.services.CredentialsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class OpenGolfConfiguration extends WebSecurityConfigurerAdapter {

    @Autowired
    protected CredentialsService credentialsService;

    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .anonymous().and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .addFilterBefore(new StatelessAuthenticationFilter(this.credentialsService), AnonymousAuthenticationFilter.class)
            .exceptionHandling()
                .authenticationEntryPoint((req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
                .accessDeniedHandler((req, res, ex) -> res.setStatus(HttpStatus.FORBIDDEN.value()));
    }

}
