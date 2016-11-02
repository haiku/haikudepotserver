/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.security.AbstractUserAuthenticationAware;

/**
 * <p>This abstract superclass of the API implementations allows access to the presently authenticated user.  See the
 * superclass for detail.</p>
 */

public abstract class AbstractApiImpl extends AbstractUserAuthenticationAware {

    protected Architecture getArchitecture(ObjectContext context, String architectureCode) throws ObjectNotFoundException {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(architectureCode), "an architecture code is required to get the architecture");
        return Architecture.getByCode(context,architectureCode)
                .orElseThrow(() -> new ObjectNotFoundException(Architecture.class.getSimpleName(), architectureCode));
    }

    protected NaturalLanguage getNaturalLanguage(ObjectContext context, String naturalLanguageCode) throws ObjectNotFoundException  {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));
        return NaturalLanguage.getByCode(context, naturalLanguageCode)
                .orElseThrow(() -> new ObjectNotFoundException(NaturalLanguage.class.getSimpleName(), naturalLanguageCode));
    }

    /**
     * <p>Obtains and returns the repository based on the supplied code.  It will throw a runtime exception if the code
     * is not supplied or if no repository was able to be found for the code supplied.</p>
     */

    protected Repository getRepository(ObjectContext context, String repositoryCode) throws ObjectNotFoundException {
        Preconditions.checkNotNull(context);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(repositoryCode), "a repository code is required to search for the repository");
        return Repository.getByCode(context, repositoryCode)
                .orElseThrow(() -> new ObjectNotFoundException(Repository.class.getSimpleName(), repositoryCode));
    }

}
