/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * <p>An instance of this job can be submitted to an implementation of
 * {@link org.haikuos.haikudepotserver.userrating.UserRatingDerivationService} in order to get the
 * aggregated user rating re-calculated for the package.  It is possible that the job can also be configured
 * such that the derivation is undertaken not for just one package, but for every package in the system.</p>
 */

public class UserRatingDerivationJob {

    /**
     * <P>This is a special package name that cannot occur in actual package names which means;
     * do the user rating derivation for all of the packages.</P>
     */

    final static String PKG_NAME_ALL = "-*-";

    public final static UserRatingDerivationJob JOB_ALL_PKG = new UserRatingDerivationJob(PKG_NAME_ALL);

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

    public boolean appliesToAllPkgs() {
        return getPkgName().equals(PKG_NAME_ALL);
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
