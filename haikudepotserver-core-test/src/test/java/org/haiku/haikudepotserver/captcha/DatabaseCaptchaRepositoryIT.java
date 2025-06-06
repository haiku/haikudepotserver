/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.captcha;

import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;

import java.sql.Timestamp;
import java.time.Clock;

@ContextConfiguration(classes = TestConfig.class)
public class DatabaseCaptchaRepositoryIT extends AbstractIntegrationTest {

    @Value("${hds.captcha.expiry-seconds:120}")
    private Long expirySeconds;

    @Resource
    private DatabaseCaptchaRepository databaseCaptchaRepository;

    /**
     * <p>This {@link Response} is old enough to be purged so check that after doing a purge
     * that the object is absent.</p>
     */

    @Test
    public void testPurgeExpired() {

        long nowMillis = Clock.systemUTC().millis();

        {
            Timestamp expiredTimestamp = new Timestamp(nowMillis - (expirySeconds + 1) * 1000);
            ObjectContext context = serverRuntime.newContext();
            Response response = context.newObject(Response.class);
            response.setCreateTimestamp(expiredTimestamp);
            response.setResponse("RESPONSE");
            response.setToken("TOKEN");
            context.commitChanges();
        }

        // -------------------------
        databaseCaptchaRepository.purgeExpired();
        // -------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            long countFound = ObjectSelect.query(Response.class).where(Response.TOKEN.eq("TOKEN")).selectCount(context);
            Assertions.assertThat(countFound).isEqualTo(0);
        }

    }

    /**
     * <p>This {@link Response} is not old enough to be purged so check that after doing a purge
     * that the object is still present.</p>
     */

    @Test
    public void testPurgeNoneFound() {

        long nowMillis = Clock.systemUTC().millis();

        {
            Timestamp notExpiredTimestamp = new Timestamp(nowMillis);
            ObjectContext context = serverRuntime.newContext();
            Response response = context.newObject(Response.class);
            response.setCreateTimestamp(notExpiredTimestamp);
            response.setResponse("RESPONSE");
            response.setToken("TOKEN");
            context.commitChanges();
        }

        // -------------------------
        databaseCaptchaRepository.purgeExpired();
        // -------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            long countFound = ObjectSelect.query(Response.class).where(Response.TOKEN.eq("TOKEN")).selectCount(context);
            Assertions.assertThat(countFound).isEqualTo(1); // still there
        }

    }

}
