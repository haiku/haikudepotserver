package org.haiku.haikudepotserver.security;

import org.haiku.haikudepotserver.security.model.AuthenticationService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Optional;

public class UserAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

    private final AuthenticationService authenticationService;

    public UserAuthenticationProvider(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            return authenticate((UsernamePasswordAuthenticationToken) authentication);
        }

        return null;
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return aClass == UsernamePasswordAuthenticationToken.class;
    }

    private Authentication authenticate(UsernamePasswordAuthenticationToken authenticationToken) {
        return authenticateUsernamePassword(
                Optional.ofNullable(authenticationToken.getPrincipal())
                        .map(Object::toString)
                        .orElse(null),
                Optional.ofNullable(authenticationToken.getCredentials())
                        .map(Object::toString)
                        .orElse(null));
    }

    private Authentication authenticateUsernamePassword(String username, String password) {
        Authentication authentication = authenticationService.authenticateByNicknameAndPassword(username, password)
                .map(UserAuthentication::new)
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));
        authentication.setAuthenticated(true);
        return authentication;
    }

}
