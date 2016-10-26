/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgProminenceAndUserRatingSpreadsheetJobSpecification;
import org.springframework.util.ObjectUtils;

public class AuthorizationRulesSpreadsheetJobSpecification extends AbstractJobSpecification {

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(PkgProminenceAndUserRatingSpreadsheetJobSpecification.class.isAssignableFrom(other.getClass())) {
            return ObjectUtils.nullSafeEquals(other.getOwnerUserNickname(), getOwnerUserNickname());
        }

        return false;
    }

}
