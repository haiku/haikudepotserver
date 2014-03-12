/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.PkgCategory;
import org.haikuos.haikudepotserver.support.AbstractSearchSpecification;

/**
 * <p>This model object specifies the parameters of a search into the system for packages.  See the
 * {@link org.haikuos.haikudepotserver.pkg.PkgService} for further detail on this.</p>
 */

public class PkgSearchSpecification extends AbstractSearchSpecification {

    public enum SortOrdering {
        NAME,
        VERSIONCREATETIMESTAMP,
        VERSIONVIEWCOUNTER
    }

    private Architecture architecture;

    private PkgCategory pkgCategory;

    private Number daysSinceLatestVersion;

    private SortOrdering sortOrdering;

    public Architecture getArchitecture() {
        return architecture;
    }

    public void setArchitecture(Architecture architecture) {
        this.architecture = architecture;
    }

    public PkgCategory getPkgCategory() {
        return pkgCategory;
    }

    public void setPkgCategory(PkgCategory pkgCategory) {
        this.pkgCategory = pkgCategory;
    }

    public SortOrdering getSortOrdering() {
        return sortOrdering;
    }

    public void setSortOrdering(SortOrdering sortOrdering) {
        this.sortOrdering = sortOrdering;
    }

    public Number getDaysSinceLatestVersion() {
        return daysSinceLatestVersion;
    }

    public void setDaysSinceLatestVersion(Number daysSinceLatestVersion) {
        this.daysSinceLatestVersion = daysSinceLatestVersion;
    }

}
