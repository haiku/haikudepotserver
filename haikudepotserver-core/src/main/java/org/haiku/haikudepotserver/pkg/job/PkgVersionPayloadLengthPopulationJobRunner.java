/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgVersionPayloadLengthPopulationJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * <p>It can come to be that a pkg version is missing its payload length; perhaps because it was unable to get the
 * length at the time or something.  In any case, this job will go through those PkgVersions that should probably
 * have payload lengths and will populate them.</p>
 */

@Component
public class PkgVersionPayloadLengthPopulationJobRunner
        extends AbstractJobRunner<PkgVersionPayloadLengthPopulationJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgVersionPayloadLengthPopulationJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Override
    public void run(JobService jobService, PkgVersionPayloadLengthPopulationJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);

        ObjectContext context = serverRuntime.getContext();

        // we want to fetch the ObjectIds of PkgVersions that need to be handled.

        List<PkgVersion> pkgVersions;

        {
            SelectQuery query = new SelectQuery(
                    PkgVersion.class,
                    ExpressionHelper.andAll(ImmutableList.of(
                                    ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE),
                                    ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY + "." + Pkg.ACTIVE_PROPERTY, Boolean.TRUE),
                                    ExpressionFactory.matchExp(PkgVersion.IS_LATEST_PROPERTY, Boolean.TRUE),
                                    ExpressionFactory.matchExp(PkgVersion.PAYLOAD_LENGTH_PROPERTY, null)
                            )
                    )
            );

            query.setPageSize(50);

            pkgVersions = context.performQuery(query);
        }

        LOGGER.info("did find {} package versions that need payload lengths to be populated", pkgVersions.size());

        for(int i=0;i<pkgVersions.size();i++) {
            PkgVersion pkgVersion = pkgVersions.get(i);
            long len = -1;

            try {
                len = pkgOrchestrationService.payloadLength(pkgVersion);
            }
            catch(IOException ioe) {
                LOGGER.error("unable to get the payload length for " + pkgVersion.toString(), ioe);
            }

            if(len > 0) {
                pkgVersion.setPayloadLength(pkgOrchestrationService.payloadLength(pkgVersion));
                context.commitChanges();
            }

            jobService.setJobProgressPercent(
                    specification.getGuid(),
                    i*100 / pkgVersions.size()
            );

        }

    }

}
