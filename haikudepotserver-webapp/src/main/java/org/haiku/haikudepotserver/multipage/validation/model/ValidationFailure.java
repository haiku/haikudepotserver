/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.validation.model;

public interface ValidationFailure {

    String getKey();

    Object[] getParams();

}
