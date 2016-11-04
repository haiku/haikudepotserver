/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshotImage;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotOptimizationJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;

/**
 * <p>This job runner is able to optimize the screenshot images by using the PNG optimizer.  It is triggered
 * from the &quot;PkgScreenshotController&quot; so that the HTTP
 * import of the image can happen quickly, but the optimization can take its time in the background.</p>
 */

@Component
public class PkgScreenshotOptimizationJobRunner extends AbstractJobRunner<PkgScreenshotOptimizationJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgScreenshotOptimizationJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PngOptimizationService pngOptimizationService;

    @Override
    public void run(
            JobService jobService,
            PkgScreenshotOptimizationJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null!= jobService);
        Preconditions.checkArgument(null!=specification);

        long startMs = System.currentTimeMillis();

        if(pngOptimizationService.identityOptimization()) {
            LOGGER.info("will optimize {} screenshot images", specification.getPkgScreenshotCodes().size());

            for (String pkgScreenshotCode : specification.getPkgScreenshotCodes()) {

                ObjectContext context = serverRuntime.getContext();
                Optional<PkgScreenshot> pkgScreenshotOptional = PkgScreenshot.getByCode(context, pkgScreenshotCode);

                if (pkgScreenshotOptional.isPresent()) {

                    PkgScreenshot pkgScreenshot = pkgScreenshotOptional.get();
                    PkgScreenshotImage pkgScreenshotImage = pkgScreenshotOptional.get().getPkgScreenshotImage().get();

                    if (pkgScreenshotImage.getMediaType().getCode().equals(MediaType.PNG.withoutParameters().toString())) {

                        byte[] originalImageData = pkgScreenshotImage.getData();
                        byte[] optimizedData = pngOptimizationService.optimize(originalImageData);

                        if(optimizedData.length < originalImageData.length) {
                            pkgScreenshotImage.setData(optimizedData);
                            pkgScreenshot.setLength(optimizedData.length);
                            pkgScreenshot.setModifyTimestamp(new Date());
                            context.commitChanges();
                            LOGGER.debug("did store optimized image for pkg screenshot; {}", pkgScreenshotCode);
                        }
                        else {
                            LOGGER.warn("optimized data is larger than the original data for pkg screenshot; {}", pkgScreenshotCode);
                        }

                    } else {
                        LOGGER.warn(
                                "pkg screenshot '{}' in unknown image format '{}'; will ignore",
                                pkgScreenshotCode,
                                pkgScreenshotImage.getMediaType().getCode());
                    }
                } else {
                    LOGGER.warn("attempt to optimize pkg screenshot that does not exist; {}", pkgScreenshotCode);
                }
            }

            LOGGER.info(
                    "did optimize {} screenshot images in {}ms",
                    specification.getPkgScreenshotCodes().size(),
                    System.currentTimeMillis() - startMs);
        }
        else {
            LOGGER.info("png optimizer is not configured; will not optimize pkg screenshots");
        }

    }
}
