/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.support;

import java.util.List;

/**
 * <p>Some of the API performs searches into various data sets such as packages.  This abstract class is able to
 * provide some common material for the returned data.</p>
 */

public abstract class AbstractSearchResult<T> {

    /**
     * <p>This is a list of the result objects.</p>
     */

    public List<T> items;

    /**
     * <p>Ignoring the offset and the max, this value is the total number of objects that could have been
     * returned.</p>
     */

    public Long total;

}
