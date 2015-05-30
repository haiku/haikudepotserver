/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating.model;

import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.support.AbstractSearchSpecification;

/**
 * <p>This model object represents the search that can be undertaken into the user ratings.</p>
 */

public class UserRatingSearchSpecification extends AbstractSearchSpecification {

    private User user;

    private Pkg pkg;

    private Repository repository;

    private Architecture architecture;

    private PkgVersion pkgVersion;

    private Integer daysSinceCreated;

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Pkg getPkg() {
        return pkg;
    }

    public void setPkg(Pkg pkg) {
        this.pkg = pkg;
    }

    public PkgVersion getPkgVersion() {
        return pkgVersion;
    }

    public void setPkgVersion(PkgVersion pkgVersion) {
        this.pkgVersion = pkgVersion;
    }

    public Integer getDaysSinceCreated() {
        return daysSinceCreated;
    }

    public void setDaysSinceCreated(Integer daysSinceCreated) {
        this.daysSinceCreated = daysSinceCreated;
    }

    public Architecture getArchitecture() {
        return architecture;
    }

    public void setArchitecture(Architecture architecture) {
        this.architecture = architecture;
    }

}
