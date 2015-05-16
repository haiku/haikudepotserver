/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.captcha;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.haikuos.haikudepotserver.captcha.model.CaptchaRepository;
import org.haikuos.haikudepotserver.dataobjects.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * <p>This object stores the captchas in a database for later retrieval.  It uses the Cayenne object-relational
 * system to access the database objects.</p>
 */

public class DatabaseCaptchaRepository implements CaptchaRepository {

    protected static Logger LOGGER = LoggerFactory.getLogger(DatabaseCaptchaRepository.class);

    private ServerRuntime serverRuntime;

    private Long expirySeconds;

    public ServerRuntime getServerRuntime() {
        return serverRuntime;
    }

    public void setServerRuntime(ServerRuntime serverRuntime) {
        this.serverRuntime = serverRuntime;
    }

    public void setExpirySeconds(Long expirySeconds) {
        this.expirySeconds = expirySeconds;
    }

    public void init() {
        purgeExpired();
    }

    @Override
    public void purgeExpired() {
        Preconditions.checkNotNull(serverRuntime);

        EJBQLQuery q = new EJBQLQuery(String.format(
                "DELETE FROM %s r WHERE r.createTimestamp < :expiryTimestamp",
                Response.class.getSimpleName()));

        q.setParameter("expiryTimestamp", new Timestamp(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(expirySeconds)));
        getServerRuntime().getContext().performQuery(q);
    }

    @Override
    public boolean delete(String token) {
        Preconditions.checkState(!Strings.isNullOrEmpty(token));
        Preconditions.checkNotNull(serverRuntime);

        ObjectContext objectContext = serverRuntime.getContext();

        Optional<Response> responseOptional = Response.getByToken(objectContext, token);

        if(responseOptional.isPresent()) {
            objectContext.deleteObjects(responseOptional.get());
            objectContext.commitChanges();
            LOGGER.info("did delete captcha response with token; {}", token);
            return true;
        }

        return false;
    }

    @Override
    public String get(String token) {
        Preconditions.checkState(!Strings.isNullOrEmpty(token));
        Preconditions.checkNotNull(serverRuntime);

        ObjectContext objectContext = serverRuntime.getContext();

        Optional<Response> responseOptional = Response.getByToken(objectContext, token);

        if(responseOptional.isPresent()) {
            String result = responseOptional.get().getResponse();
            delete(token);
            return result;
        }

        return null;
    }

    @Override
    public void store(String token, String response) {
        Preconditions.checkState(!Strings.isNullOrEmpty(token));
        Preconditions.checkState(!Strings.isNullOrEmpty(response));
        Preconditions.checkNotNull(serverRuntime);

        ObjectContext objectContext = serverRuntime.getContext();

        Response r = objectContext.newObject(Response.class);

        r.setToken(token);
        r.setResponse(response);
        r.setCreateTimestamp(new Date());

        objectContext.commitChanges();

        LOGGER.info("stored captcha response with token {}", token);
    }

}
