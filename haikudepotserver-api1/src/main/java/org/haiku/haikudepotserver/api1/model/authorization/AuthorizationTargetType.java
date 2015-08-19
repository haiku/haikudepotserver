/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.authorization;

/**
 * <p>This enum is used to identify what type of target the authorization applies to.</p>
 */

public enum AuthorizationTargetType {
    PKG,
    USER,
    REPOSITORY,
    USERRATING
}
