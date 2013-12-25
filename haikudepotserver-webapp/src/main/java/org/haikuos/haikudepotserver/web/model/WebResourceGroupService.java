/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.web.model;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.util.List;

/**
 * <p>This service just keeps all of the web resource groups together and also keeps any properties that are necessary
 * to access the web resources in one place.</p>
 */

public class WebResourceGroupService {

    private String guid;

    private List<WebResourceGroup> webResourceGroups;

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public List<WebResourceGroup> getWebResourceGroups() {
        return webResourceGroups;
    }

    public void setWebResourceGroups(List<WebResourceGroup> webResourceGroups) {
        this.webResourceGroups = webResourceGroups;
    }

    public Optional<WebResourceGroup> getWebResourceGroup(final String code) {
        Preconditions.checkNotNull(code);
        return Iterables.tryFind(
                getWebResourceGroups(),
                new Predicate<WebResourceGroup>() {
                    @Override
                    public boolean apply(org.haikuos.haikudepotserver.web.model.WebResourceGroup input) {
                        return input.getCode().equals(code);
                    }
                }
        );
    }
}
