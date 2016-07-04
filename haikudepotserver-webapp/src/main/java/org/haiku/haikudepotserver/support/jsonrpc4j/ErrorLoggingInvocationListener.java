/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.jsonrpc4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.InvocationListener;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.api1.support.CaptchaBadResponseException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.api1.support.ValidationException;
import org.haiku.haikudepotserver.support.ImageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * <p>This plugins into the JSON-RPC server system in order to handle logging errors.</p>
 */

public class ErrorLoggingInvocationListener implements InvocationListener {

    protected static Logger LOGGER = LoggerFactory.getLogger(ImageHelper.class);

    private boolean isMainFlowThrowable(Throwable t) {
        return
                ObjectNotFoundException.class.isAssignableFrom(t.getClass())
                        || AuthorizationFailureException.class.isAssignableFrom(t.getClass())
                        || CaptchaBadResponseException.class.isAssignableFrom(t.getClass())
                        || org.apache.cayenne.validation.ValidationException.class.isAssignableFrom(t.getClass())
                        || ValidationException.class.isAssignableFrom(t.getClass());
    }

    @Override
    public void willInvoke(Method method, List<JsonNode> arguments) {
    }

    @Override
    public void didInvoke(Method method, List<JsonNode> arguments, Object result, Throwable t, long duration) {
        Preconditions.checkArgument(null!=method, "a method is required to report an invocation");

        if(null!=t) {
            if (InvocationTargetException.class.isInstance(t)) {
                //noinspection ThrowableResultOfMethodCallIgnored
                t = InvocationTargetException.class.cast(t).getTargetException();
            }

            Logger logger = LoggerFactory.getLogger(method.getDeclaringClass());
            String methodDebugString = method.getDeclaringClass().getSimpleName() + '#' + method.getName();

            if (isMainFlowThrowable(t)) {

                String tMessage = t.getMessage();

                StringBuilder msg = new StringBuilder("jrpc @ ");
                msg.append(methodDebugString);
                msg.append("; ");
                msg.append(t.getClass().getSimpleName());

                if(!Strings.isNullOrEmpty(tMessage)) {
                    msg.append(" -- ");
                    msg.append(tMessage);
                }

                logger.info(msg.toString());

            } else {
                logger.error("jrpc @ " + methodDebugString + "; " + t.getClass().getSimpleName(), t);
            }
        }

    }

}
