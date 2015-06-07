/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.repository;

import org.haikuos.haikudepotserver.api1.support.AbstractSearchResult;

public class SearchRepositoriesResult extends AbstractSearchResult<SearchRepositoriesResult.Repository> {

    public static class Repository {

        public Boolean active;

        public String code;

        /**
         * @since 2015-07-07
         */

        public String name;

    }

}
