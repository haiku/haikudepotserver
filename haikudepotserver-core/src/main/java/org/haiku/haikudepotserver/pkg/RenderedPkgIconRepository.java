/*
 * Copyright 2015-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * <p>This object will maintain a repository and cache of package's icons at various sizes.</p>
 */

@Repository
public interface RenderedPkgIconRepository {

    int SIZE_MAX = 512;
    int SIZE_MIN = 16;

    /**
     * <p>Removes any cached icons for the nominated pkg.</p>
     */

    void evict(ObjectContext context, PkgSupplement pkgSupplement);

    /**
     * <p>Optionally produces a bitmap icon at the specified size.  If no icon can be produced then it
     * will return an absent optional.</p>
     */

    Optional<byte[]> render(int size, ObjectContext context, PkgSupplement pkgSupplement);

    /**
     * <p>This renders a generic icon that is not for a specific package.</p>
     */

    byte[] renderGeneric(int size);

}
