/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import org.apache.cayenne.ObjectContext;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.security.AbstractUserAuthenticationAware;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;


/**
 * <p>This abstract superclass of the API implementations allows access to the presently authenticated user.  See the
 * superclass for detail.</p>
 */

public abstract class AbstractApiImpl extends AbstractUserAuthenticationAware {

    @Autowired(required = false)
    private HttpServletRequest request;

    protected Architecture getArchitecture(ObjectContext context, String architectureCode) throws ObjectNotFoundException {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(architectureCode), "an architecture code is required to get the architecture");

        Optional<Architecture> architectureOptional = Architecture.getByCode(context,architectureCode);

        if(!architectureOptional.isPresent()) {
            throw new ObjectNotFoundException(Architecture.class.getSimpleName(), architectureCode);
        }

        return architectureOptional.get();
    }

    protected NaturalLanguage getNaturalLanguage(ObjectContext context, String naturalLanguageCode) throws ObjectNotFoundException  {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));

        Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.getByCode(context, naturalLanguageCode);

        if(!naturalLanguageOptional.isPresent()) {
            throw new ObjectNotFoundException(NaturalLanguage.class.getSimpleName(), naturalLanguageCode);
        }

        return naturalLanguageOptional.get();
    }

    /**
     * <p>This method will try to obtain some sort of identifier for the current client; such as their IP address.</p>
     */

    protected String getRemoteIdentifier() {
        String result = null;

        if(null!=request) {
            result = request.getHeader(HttpHeaders.X_FORWARDED_FOR);

            if(!Strings.isNullOrEmpty(result)) {
                return result;
            }

            result = request.getRemoteAddr();

            if(!Strings.isNullOrEmpty(result)) {
                return result;
            }
        }

        return result;
    }

}
