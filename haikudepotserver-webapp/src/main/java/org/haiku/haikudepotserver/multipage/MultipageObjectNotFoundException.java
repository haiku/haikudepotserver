/*
 * Copyright 2014-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * <p>This exception will return a 404 when it arises.</p>
 */

@ResponseStatus(HttpStatus.NOT_FOUND)
public class MultipageObjectNotFoundException extends Exception {

    private final String entityName;
    private final Object identifier;

    public MultipageObjectNotFoundException(String entityName, Object identifier) {
        super();

        if (null == entityName || entityName.isEmpty()) {
            throw new IllegalStateException("the entity name is required");
        }

        if (null == identifier) {
            throw new IllegalStateException("the identifier is required");
        }

        this.entityName = entityName;
        this.identifier = identifier;
    }

    public String getEntityName() {
        return entityName;
    }

    public Object getIdentifier() {
        return identifier;
    }

    @Override
    public String getMessage() {
        return String.format("the entity %s was not able to be found with the identifier %s",getEntityName(),getIdentifier().toString());
    }

}
