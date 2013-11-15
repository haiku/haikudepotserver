/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.support;

/**
 * <p>This exception is thrown when the system is not able to find an object in the system as part of an API
 * invocation.</p>
 */

public class ObjectNotFoundException extends Exception {

    public String entityName;
    public Object identifier;

    public ObjectNotFoundException(String entityName, Object identifier) {
        super();

        if(null==entityName || 0==entityName.length()) {
            throw new IllegalStateException("the entity name is required");
        }

        if(null==identifier) {
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
