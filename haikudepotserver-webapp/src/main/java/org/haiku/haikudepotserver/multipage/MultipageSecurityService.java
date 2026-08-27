package org.haiku.haikudepotserver.multipage;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.AuthenticationHelper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

// TODO; tie-up with `AbstractUserAuthenticationAware`???
// TODO; remove the `ServerRuntime`???

@Service
public class MultipageSecurityService {

    private final ServerRuntime serverRuntime;

    public MultipageSecurityService(ServerRuntime serverRuntime) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
    }

    /**
     * <P>This method will (optionally) return a user that represents the currently authenticated user.  It will not
     * return null.</P>
     */

    public Optional<org.haiku.haikudepotserver.multipage.model.User> tryObtainAuthenticatedUser() {
        return tryObtainAuthenticatedDataModelUser(serverRuntime.newContext())
                .map(u -> new org.haiku.haikudepotserver.multipage.model.User(u.getNickname()));
    }

    private static Optional<User> tryObtainAuthenticatedDataModelUser(ObjectContext objectContext) {
        Preconditions.checkArgument(null != objectContext, "the object context must be provided");
        return AuthenticationHelper.tryGetUserForAuthentication(objectContext, SecurityContextHolder.getContext().getAuthentication());
    }

}
