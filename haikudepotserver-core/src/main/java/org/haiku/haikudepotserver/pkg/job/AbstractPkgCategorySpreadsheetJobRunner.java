/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

abstract class AbstractPkgCategorySpreadsheetJobRunner<T extends JobSpecification>
        extends AbstractJobRunner<T> {

    static final int COLUMN_NONE = 3;

    protected ServerRuntime serverRuntime;
    protected PkgService pkgService;

    public AbstractPkgCategorySpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            PkgService pkgService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgService = Preconditions.checkNotNull(pkgService);
    }

    List<String> getPkgCategoryCodes() {
        return PkgCategory.getAll(serverRuntime.newContext())
                .stream()
                .map(PkgCategory::getCode)
                .collect(Collectors.toList());
    }

    String[] getHeadingRow(List<String> pkgCategoryCodes) {
        List<String> headings = new ArrayList<>();
        headings.add("pkg-name"); // 0
        headings.add("repository-codes"); // 1
        headings.add("any-summary"); // 2
        headings.add("none"); // 3
        Collections.addAll(headings, pkgCategoryCodes.toArray(new String[pkgCategoryCodes.size()]));
        headings.add("action");
        return headings.toArray(new String[headings.size()]);
    }

}
