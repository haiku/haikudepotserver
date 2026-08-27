package org.haiku.haikudepotserver.multipage.model;

import jakarta.annotation.Nullable;
import org.springframework.web.util.UriComponents;

/**
 * <p>Couples a user together with some URLs to access controls for the user.</p>
 */

public record UserAndNavigation(
        @Nullable User user,
        @Nullable UriComponents viewUriComponents,
        UriComponents logoutUriComponents,
        UriComponents loginUriComponents
) {
}
