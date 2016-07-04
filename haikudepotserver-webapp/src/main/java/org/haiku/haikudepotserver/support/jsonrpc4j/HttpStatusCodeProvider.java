package org.haiku.haikudepotserver.support.jsonrpc4j;

import javax.servlet.http.HttpServletResponse;

/**
 * <p>Ensures that JSON-RPC invocations always return 200 HTTP status codes.</p>
 */

public class HttpStatusCodeProvider implements com.googlecode.jsonrpc4j.HttpStatusCodeProvider {

    @Override
    public int getHttpStatusCode(int resultCode) {
        return HttpServletResponse.SC_OK;
    }

}
