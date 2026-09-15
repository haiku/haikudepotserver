/*
 * Copyright 2015-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

public class WebConstants {

    public final static String SEGMENT_JS = "__js";
    public final static String SEGMENT_CSS = "__css";
    public final static String SEGMENT_IMG = "__img";
    public final static String SEGMENT_SECURITY = "__security";

    public final static String KEY_NATURALLANGUAGECODE = "locale";
    public final static String KEY_REDIRECT_URI = "redirect_uri";

    public final static String PATH_COMPONENT_SECURED = "__secured";

    public final static String SEGMENT_LOGIN = "login";
    public final static String SEGMENT_LOGIN_PROCESSING = "login-processing";
    public final static String SEGMENT_LOGOUT = "logout";

    /**
     * <p>This key stores against an {@link jakarta.servlet.http.HttpSession} to
     * store the final URL before the user is redirected to the OAuth2
     * authentication endpoint. When the request come back successfully from
     * the identity provider, this value be again obtained from the session,
     * and the user redirected back again.</p>
     */
    public final static String KEY_FINAL_REDIRECT_URI = "hds.auth.final_uri";
}
