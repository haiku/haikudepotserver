/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import java.util.Set;

public class TriggerImportRepositoryRequest {

    /**
     * @since 2015-06-22
     */

    public String repositoryCode;

    /**
     * @since 2015-06-11
     */

    public Set<String> repositorySourceCodes;

}
