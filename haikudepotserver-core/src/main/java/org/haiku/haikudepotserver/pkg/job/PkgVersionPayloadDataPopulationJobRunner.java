/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgImportService;
import org.haiku.haikudepotserver.pkg.model.PkgVersionPayloadDataPopulationJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <p>It can come to be that a pkg version is missing its payload length; perhaps because it was unable to get the
 * length at the time or something.  In any case, this job will go through those PkgVersions that should probably
 * have payload lengths and will populate them.</p>
 */

@Component
public class PkgVersionPayloadDataPopulationJobRunner
        extends AbstractJobRunner<PkgVersionPayloadDataPopulationJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgVersionPayloadDataPopulationJobRunner.class);

    private final ServerRuntime serverRuntime;
    private final PkgImportService pkgImportService;
    private final Pattern allowedPkgNamePattern;

    public PkgVersionPayloadDataPopulationJobRunner(
            ServerRuntime serverRuntime,
            PkgImportService pkgImportService,
            @Value("${hds.repository.import.allowed-pkg-name-pattern:}") String allowedPkgNamePattern) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgImportService = Preconditions.checkNotNull(pkgImportService);
        this.allowedPkgNamePattern = Optional.ofNullable(allowedPkgNamePattern)
                .filter(StringUtils::isNotEmpty)
                .map(Pattern::compile)
                .orElse(null);
    }

    @Override
    public Class<PkgVersionPayloadDataPopulationJobSpecification> getSupportedSpecificationClass() {
        return PkgVersionPayloadDataPopulationJobSpecification.class;
    }

    @Override
    public void run(
            JobService jobService,
            PkgVersionPayloadDataPopulationJobSpecification specification)
            throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);

        ObjectContext context = serverRuntime.newContext();

        int lastPercentage = 0;
        int countSkipped = 0;

        long count = createPkgVersionObjectSelect()
                .count()
                        .selectFirst(context);

        // we want to fetch the ObjectIds of PkgVersions that need to be handled.

        List<PkgVersion> pkgVersions = createPkgVersionObjectSelect()
                .orderBy(PkgVersion.PKG.dot(Pkg.NAME).asc())
                .pageSize(50)
                .select(context);

        LOGGER.info("did find {} package versions to be populate from package data", count);

        for (int i = 0; i < pkgVersions.size(); i++) {
            String pkgName = pkgVersions.get(i).getPkg().getName();

            if (null == allowedPkgNamePattern || allowedPkgNamePattern.matcher(pkgName).matches()) {
                ObjectContext pkgVersionContext = serverRuntime.newContext();
                PkgVersion pkgVersion = pkgVersionContext.localObject(pkgVersions.get(i));

                if (pkgImportService.shouldPopulateFromPayload(pkgVersion)) {
                    LOGGER.debug("will process package [{}] from payload", pkgName);
                    pkgImportService.populateFromPayload(pkgVersionContext, pkgVersion);
                    pkgVersionContext.commitChanges();
                    LOGGER.debug("did process package [{}] from payload", pkgName);
                } else {
                    LOGGER.info("skip package [{}] as it should not populate from payload", pkgName);
                }
            } else {
                countSkipped++;

                if (countSkipped < 10) {
                    LOGGER.info("skip package [{}] as it does not match the filter", pkgName);
                }
            }

            int updatedPercentage = (int) ((i * 100) / count);

            if (updatedPercentage > lastPercentage) {
                jobService.setJobProgressPercent(specification.getGuid(),updatedPercentage);
                lastPercentage = updatedPercentage;
            }
        }

        if (0 != countSkipped) {
            LOGGER.info("did skip {} packages", countSkipped);
        }

    }

    private ObjectSelect<PkgVersion> createPkgVersionObjectSelect() {
        return ObjectSelect.query(PkgVersion.class)
                .where(PkgVersion.ACTIVE.isTrue())
                .and(PkgVersion.PKG.dot(Pkg.ACTIVE).isTrue())
                .and(PkgVersion.IS_LATEST.isTrue());
    }

}
