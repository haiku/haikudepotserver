/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.spring;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.model.User;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Collections;

public class UserAuthenticationProvider implements AuthenticationProvider {

    private ServerRuntime serverRuntime;

    public ServerRuntime getServerRuntime() {
        return serverRuntime;
    }

    public void setServerRuntime(ServerRuntime serverRuntime) {
        this.serverRuntime = serverRuntime;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        Preconditions.checkNotNull(authentication);
        Preconditions.checkNotNull(serverRuntime);

        String nickname = authentication.getName();
        String passwordClear = (String) authentication.getCredentials();

        if(Strings.isNullOrEmpty(nickname) || Strings.isNullOrEmpty(passwordClear)) {
            throw new BadCredentialsException("no nickname or password supplied; unable to authenticate the user");
        }

        ObjectContext objectContext = serverRuntime.getContext();

        Optional<User> userOptional = User.getByNickname(objectContext, nickname);

        if(!userOptional.isPresent()) {
            throw new UsernameNotFoundException("unable to find the user; "+nickname);
        }

        User user = userOptional.get();
        String hash = Hashing.sha256().hashUnencodedChars(user.getPasswordSalt() + passwordClear).toString();

        if(hash.equals(user.getPasswordHash())) {
            return new UsernamePasswordAuthenticationToken(
                    user.getNickname(),
                    passwordClear,
                    Collections.<GrantedAuthority>emptySet());
        }

        throw new BadCredentialsException("bad password supplied");
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return true;
    }
}
