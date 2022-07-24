/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.security.AbstractUserAuthenticationAware;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;

public abstract class AbstractApiService extends AbstractUserAuthenticationAware {

    protected Pkg getPkg(ObjectContext context, String pkgName) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));
        return Pkg.tryGetByName(context, pkgName)
                .orElseThrow(() -> new ObjectNotFoundException(Pkg.class.getSimpleName(), pkgName));
    }

    protected Architecture getArchitecture(ObjectContext context, String architectureCode) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(architectureCode), "an architecture code is required to get the architecture");
        return Architecture.tryGetByCode(context,architectureCode)
                .orElseThrow(() -> new ObjectNotFoundException(Architecture.class.getSimpleName(), architectureCode));
    }

    protected NaturalLanguage getNaturalLanguage(ObjectContext context, String naturalLanguageCode) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));
        return NaturalLanguage.tryGetByCode(context, naturalLanguageCode)
                .orElseThrow(() -> new ObjectNotFoundException(NaturalLanguage.class.getSimpleName(), naturalLanguageCode));
    }

    /**
     * <p>Obtains and returns the repository based on the supplied code.  It will throw a runtime exception if the code
     * is not supplied or if no repository was able to be found for the code supplied.</p>
     */

    protected Repository getRepository(ObjectContext context, String repositoryCode) {
        Preconditions.checkNotNull(context);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(repositoryCode), "a repository code is required to search for the repository");
        return Repository.tryGetByCode(context, repositoryCode)
                .orElseThrow(() -> new ObjectNotFoundException(Repository.class.getSimpleName(), repositoryCode));
    }

    /**
     * <p>Obtains and returns the repository source based on the supplied code.  It will throw a runtime exception if the code
     * is not supplied or if no repository source was able to be found for the code supplied.</p>
     */

    protected RepositorySource getRepositorySource(ObjectContext context, String repositorySourceCode) {
        Preconditions.checkNotNull(context);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(repositorySourceCode), "a repository code is required to search for the repository");
        return RepositorySource.tryGetByCode(context, repositorySourceCode)
                .orElseThrow(() -> new ObjectNotFoundException(RepositorySource.class.getSimpleName(), repositorySourceCode));
    }

}
