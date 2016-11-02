/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

/**
 * <p>Is used in some situations to process a list of items.  This is a bit like {@link java.util.function.Consumer},
 * but the method returns false if the processing should stop after processing the current T.</p>
 */

public interface StoppableConsumer<T> {

    boolean accept(T o);

}
