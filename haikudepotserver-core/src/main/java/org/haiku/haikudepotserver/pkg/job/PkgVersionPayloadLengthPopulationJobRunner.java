/*
 * Copyright 2018-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgVersionPayloadLengthPopulationJobSpecification;
import org.haiku.haikudepotserver.support.ExposureType;
import org.haiku.haikudepotserver.support.URLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * <p>It can come to be that a pkg version is missing its payload length; perhaps because it was unable to get the
 * length at the time or something.  In any case, this job will go through those PkgVersions that should probably
 * have payload lengths and will populate them.</p>
 */

@Component
public class PkgVersionPayloadLengthPopulationJobRunner
        extends AbstractJobRunner<PkgVersionPayloadLengthPopulationJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgVersionPayloadLengthPopulationJobRunner.class);

    private ServerRuntime serverRuntime;

    public PkgVersionPayloadLengthPopulationJobRunner(
            ServerRuntime serverRuntime) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
    }

    @Override
    public void run(
            JobService jobService,
            PkgVersionPayloadLengthPopulationJobSpecification specification)
            throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);

        ObjectContext context = serverRuntime.newContext();

        // we want to fetch the ObjectIds of PkgVersions that need to be handled.

        List<PkgVersion> pkgVersions = ObjectSelect.query(PkgVersion.class)
                .where(PkgVersion.ACTIVE.isTrue())
                .and(PkgVersion.PKG.dot(Pkg.ACTIVE).isTrue())
                .and(PkgVersion.IS_LATEST.isTrue())
                .and(PkgVersion.PAYLOAD_LENGTH.isNull())
                .pageSize(50)
                .select(context);

        LOGGER.info("did find {} package versions that need payload lengths to be populated", pkgVersions.size());

        for(int i=0;i<pkgVersions.size();i++) {
            PkgVersion pkgVersion = pkgVersions.get(i);
            Optional<URL> urlOptional = pkgVersion.tryGetHpkgURL(ExposureType.INTERNAL_FACING);

            if (urlOptional.isPresent()) {
                long len;

                try {
                    len = URLHelper.payloadLength(urlOptional.get());

                    if(len > 0) {
                        pkgVersion.setPayloadLength(len);
                        context.commitChanges();
                    }
                }
                catch(IOException ioe) {
                    LOGGER.error("unable to get the payload length for " + pkgVersion.toString(), ioe);
                }
            } else {
                LOGGER.info("unable to get the length of [{}] because no url" +
                        "hpkg url was able to be obtained", pkgVersion);
            }

            jobService.setJobProgressPercent(
                    specification.getGuid(),
                    i*100 / pkgVersions.size()
            );

        }

    }

}
