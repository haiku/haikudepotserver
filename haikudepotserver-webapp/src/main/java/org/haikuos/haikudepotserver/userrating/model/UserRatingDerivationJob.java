/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class UserRatingDerivationJob {

    private String pkgName;

    public UserRatingDerivationJob(String pkgName) {
        super();
        Preconditions.checkNotNull(pkgName);
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));
        this.pkgName = pkgName;
    }

    public String getPkgName() {
        return pkgName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserRatingDerivationJob that = (UserRatingDerivationJob) o;

        if (!pkgName.equals(that.getPkgName())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return pkgName.hashCode();
    }

    @Override
    public String toString() {
        return getPkgName();
    }

}
