/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.haiku.haikudepotserver.dataobjects.auto._UserUsageConditionsAgreement;

import java.io.Serial;
import java.sql.Timestamp;
import java.time.Clock;

public class UserUsageConditionsAgreement
        extends _UserUsageConditionsAgreement {

    @Serial
    private static final long serialVersionUID = 1L; 

    @Override
    protected void onPostAdd() {
        if (null == getActive()) {
            setActive(true);
        }
    }

    public void setTimestampAgreed() {
        setTimestampAgreed(new Timestamp(Clock.systemUTC().millis()));
    }

}
