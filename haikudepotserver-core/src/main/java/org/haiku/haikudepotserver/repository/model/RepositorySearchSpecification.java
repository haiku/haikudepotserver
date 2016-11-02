/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.model;

import org.haiku.haikudepotserver.support.AbstractSearchSpecification;

import java.util.List;

public class RepositorySearchSpecification extends AbstractSearchSpecification {

    public List<String> repositorySourceSearchUrls;

    public List<String> getRepositorySourceSearchUrls() {
        return repositorySourceSearchUrls;
    }

    public void setRepositorySourceSearchUrls(List<String> repositorySourceSearchUrls) {
        this.repositorySourceSearchUrls = repositorySourceSearchUrls;
    }

}
