/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Objects;

public class UserRatingSpreadsheetJobSpecification extends AbstractJobSpecification {

    private String userNickname;

    private String pkgName;

    private String repositoryCode;

    public String getRepositoryCode() {
        return repositoryCode;
    }

    public void setRepositoryCode(String repositoryCode) {
        this.repositoryCode = repositoryCode;
    }

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
                            && Objects.equals(otherSpec.getRepositoryCode(), getRepositoryCode())
                            && Objects.equals(otherSpec.getUserNickname(), getUserNickname())
                            && Objects.equals(otherSpec.getOwnerUserNickname(), getOwnerUserNickname());
        }

        return false;
    }
}
