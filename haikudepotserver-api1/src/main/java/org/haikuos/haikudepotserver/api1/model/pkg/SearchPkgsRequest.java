/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import org.haikuos.haikudepotserver.api1.support.AbstractSearchRequest;

import java.util.List;

/**
 * <p>This is the model object that is used to define the request to search for packages in the system.</p>
 */

public class SearchPkgsRequest extends AbstractSearchRequest {

    public String architectureCode;

    public List<String> pkgCategoryCodes;

}
