/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractPkgCategorySpreadsheetJobRunner<T extends JobSpecification>
        extends AbstractJobRunner<T> {

    @Resource
    protected ServerRuntime serverRuntime;

    @Resource
    protected PkgOrchestrationService pkgOrchestrationService;

    protected List<String> getPkgCategoryCodes() {
        return PkgCategory.getAll(serverRuntime.getContext())
                .stream()
                .map(PkgCategory::getCode)
                .collect(Collectors.toList());
    }

    protected String[] getHeadingRow(List<String> pkgCategoryCodes) {
        List<String> headings = new ArrayList<>();
        headings.add("pkg-name");
        headings.add("any-summary");
        headings.add("none");
        Collections.addAll(headings, pkgCategoryCodes.toArray(new String[pkgCategoryCodes.size()]));
        headings.add("action");
        return headings.toArray(new String[headings.size()]);
    }

}
