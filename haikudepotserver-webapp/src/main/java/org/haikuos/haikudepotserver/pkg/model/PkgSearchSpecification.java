/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.PkgCategory;
import org.haikuos.haikudepotserver.support.AbstractSearchSpecification;

import java.util.List;

/**
 * <p>This model object specifies the parameters of a search into the system for packages.  See the
 * {@link org.haikuos.haikudepotserver.pkg.PkgOrchestrationService} for further detail on this.</p>
 */

public class PkgSearchSpecification extends AbstractSearchSpecification {

    public enum SortOrdering {
        NAME,
        PROMINENCE,
        VERSIONCREATETIMESTAMP,
        VERSIONVIEWCOUNTER
    }

    private List<String> pkgNames;

    private List<Architecture> architectures;

    private PkgCategory pkgCategory;

    private Number daysSinceLatestVersion;

    private SortOrdering sortOrdering;

    private NaturalLanguage naturalLanguage;

    public NaturalLanguage getNaturalLanguage() {
        return naturalLanguage;
    }

    public void setNaturalLanguage(NaturalLanguage naturalLanguage) {
        this.naturalLanguage = naturalLanguage;
    }

    public List<String> getPkgNames() {
        return pkgNames;
    }

    public void setPkgNames(List<String> pkgNames) {
        this.pkgNames = pkgNames;
    }

    public List<Architecture> getArchitectures() {
        return architectures;
    }

    public void setArchitectures(List<Architecture> architectures) {
        this.architectures = architectures;
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
