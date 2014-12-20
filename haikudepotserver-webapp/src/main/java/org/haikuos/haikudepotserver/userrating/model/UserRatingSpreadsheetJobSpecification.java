/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;

public class UserRatingSpreadsheetJobSpecification extends AbstractJobSpecification {

    private String userNickname;

    private String pkgName;

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public String getPkgName() {
        return pkgName;
    }

    public void setPkgName(String pkgName) {
        this.pkgName = pkgName;
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        if(UserRatingSpreadsheetJobSpecification.class.isAssignableFrom(other.getClass())) {
            UserRatingSpreadsheetJobSpecification otherSpec = (UserRatingSpreadsheetJobSpecification) other;

            return
                    Objects.equals(otherSpec.getPkgName(), getPkgName())
                    && Objects.equals(otherSpec.getUserNickname(), getUserNickname())
                    && Objects.equals(otherSpec.getOwnerUserNickname(), getOwnerUserNickname());
        }

        return false;
    }
}
