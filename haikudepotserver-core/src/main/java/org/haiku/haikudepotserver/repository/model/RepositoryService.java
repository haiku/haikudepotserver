/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.Repository;

import java.util.List;

/**
 * <p>This service provides non-trivial operations and processes around repositories.</p>
 */

public interface RepositoryService {


    /**
     * <p>Returns all of the repositories that contain this package.</p>
     */

    List<Repository> getRepositoriesForPkg(
            ObjectContext context,
            Pkg pkg);

    List<Repository> search(ObjectContext context, RepositorySearchSpecification search);

    long total(ObjectContext context, RepositorySearchSpecification search);

}
