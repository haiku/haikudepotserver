/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotOptimizationJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * <p>This job runner is able to optimize the screenshot images by using the PNG optimizer.  It is triggered
 * from the &quot;PkgScreenshotController&quot; so that the HTTP
 * import of the image can happen quickly, but the optimization can take its time in the background.</p>
 */

@Component
public class PkgScreenshotOptimizationJobRunner extends AbstractJobRunner<PkgScreenshotOptimizationJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgScreenshotOptimizationJobRunner.class);

    private ServerRuntime serverRuntime;
    private PkgScreenshotService screenshotService;

    public PkgScreenshotOptimizationJobRunner(
            ServerRuntime serverRuntime,
            PkgScreenshotService screenshotService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.screenshotService = Preconditions.checkNotNull(screenshotService);
    }

    @Override
    public void run(
            JobService jobService,
            PkgScreenshotOptimizationJobSpecification specification) throws JobRunnerException {

        Preconditions.checkArgument(null!= jobService);
        Preconditions.checkArgument(null!=specification);

        long startMs = System.currentTimeMillis();

        LOGGER.info("will optimize {} screenshot images", specification.getPkgScreenshotCodes().size());

        for (String pkgScreenshotCode : specification.getPkgScreenshotCodes()) {

            ObjectContext context = serverRuntime.newContext();
            Optional<PkgScreenshot> pkgScreenshotOptional = PkgScreenshot.tryGetByCode(context, pkgScreenshotCode);

            if (pkgScreenshotOptional.isPresent()) {
                try {
                    if (screenshotService.optimizeScreenshot(context, pkgScreenshotOptional.get())) {
                        context.commitChanges();
                    }
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } catch (BadPkgScreenshotException bpse) {
                    throw new JobRunnerException("unable to process a screenshot image", bpse);
                }
            }

            LOGGER.info(
                    "did optimize {} screenshot images in {}ms",
                    specification.getPkgScreenshotCodes().size(),
                    System.currentTimeMillis() - startMs);
        }

    }
}
