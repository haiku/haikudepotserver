/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgIcon;
import org.haikuos.haikudepotserver.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSink;
import org.haikuos.haikudepotserver.pkg.model.PkgIconArchiveExportJobSpecification;
import org.haikuos.haikudepotserver.support.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * <p>Produce a ZIP file containing all of the icons of the packages.</p>
 */

@Component
public class PkgIconArchiveExportJobRunner extends AbstractJobRunner<PkgIconArchiveExportJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgIconArchiveExportJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Override
    public void run(
            JobOrchestrationService jobOrchestrationService,
            PkgIconArchiveExportJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobOrchestrationService);
        assert null!=jobOrchestrationService;
        Preconditions.checkArgument(null!=specification);

        long count = 0;
        long startMs = System.currentTimeMillis();

        final ObjectContext context = serverRuntime.getContext();

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobOrchestrationService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.ZIP.toString());

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        ) {

            PrefetchTreeNode prefetchTreeNode = new PrefetchTreeNode();
            prefetchTreeNode.addPath(Pkg.PKG_ICONS_PROPERTY);

            count += pkgOrchestrationService.each(
                    context,
                    prefetchTreeNode,
                    Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE)),
                    new Callback<Pkg>() {

                        @Override
                        public boolean process(Pkg pkg) {

                            List<PkgIcon> pkgIcons = pkg.getPkgIcons();

                            if(!pkgIcons.isEmpty()) {

                                try {
                                    for (PkgIcon pkgIcon : pkgIcons) {

                                        StringBuilder filename = new StringBuilder();
                                        filename.append("hds_");
                                        filename.append(getJobTypeCode());
                                        filename.append('/');
                                        filename.append(pkg.getName());
                                        filename.append('/');
                                        filename.append(pkgIcon.deriveFilename());

                                        zipOutputStream.putNextEntry(new ZipEntry(filename.toString()));
                                        zipOutputStream.write(pkgIcon.getPkgIconImage().get().getData());
                                        zipOutputStream.closeEntry();
                                    }
                                }
                                catch(IOException ioe) {
                                    throw new RuntimeException("unable to write the package " + pkg.getName() + "'s icons to a zip archive");
                                }
                            }

                            return true;
                        }

                    });

        }

        LOGGER.info(
                "did produce icon report for {} packages in {}ms",
                count,
                System.currentTimeMillis() - startMs);

    }

}
