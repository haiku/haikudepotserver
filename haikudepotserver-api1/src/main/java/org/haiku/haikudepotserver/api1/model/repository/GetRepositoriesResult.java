/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import java.util.List;

public class GetRepositoriesResult {

    public List<Repository> repositories;

    public static class Repository {

        public String code;
        public String name;

    }

}
