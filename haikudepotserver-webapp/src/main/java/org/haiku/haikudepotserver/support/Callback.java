/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

/**
 * <p>Is used in some situations to process a list of items.</p>
 */

public interface Callback<T> {

    boolean process(T o);

}
