/*
 * Copyright 2016-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.User;

import java.util.Date;
import java.util.List;

/**
 * <p>This service provides non-trivial operations and processes around repositories.</p>
 */

public interface RepositoryService {

    void setPassword(Repository repository, String passwordClear);

    boolean matchPassword(Repository repository, String passwordClear);

    Date getLastRepositoryModifyTimestampSecondAccuracy(ObjectContext context);

    /**
     * <p>Returns all of the repositories that contain this package.</p>
     */

    List<Repository> getRepositoriesForPkg(
            ObjectContext context,
            Pkg pkg);

    List<Repository> search(ObjectContext context, RepositorySearchSpecification search);

    long total(ObjectContext context, RepositorySearchSpecification search);

    /**
     * <p>Sends out an alert message in the situation that repositories do not have an
     * update within the expected number of hours.</p>
     */

    void alertForRepositoriesAbsentUpdates(ObjectContext context);

}
