/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.model;

import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.PkgCategory;
import org.haikuos.haikudepotserver.support.AbstractSearchSpecification;

import java.util.Collection;

/**
 * <p>This model object specifies the parameters of a search into the system for packages.  See the
 * {@link org.haikuos.haikudepotserver.pkg.PkgService} for further detail on this.</p>
 */

public class PkgSearchSpecification extends AbstractSearchSpecification {

    private Collection<Architecture> architectures;

    private Collection<PkgCategory> pkgCategories;

    public Collection<Architecture> getArchitectures() {
        return architectures;
    }

    public void setArchitectures(Collection<Architecture> architectures) {
        this.architectures = architectures;
    }

    public Collection<PkgCategory> getPkgCategories() {
        return pkgCategories;
    }

    public void setPkgCategories(Collection<PkgCategory> pkgCategories) {
        this.pkgCategories = pkgCategories;
    }
}
