/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgIconImage;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgIconExportArchiveJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

/**
 * <p>Produce a tar-ball file containing all of the icons of the packages.  This uses a direct query avoiding the
 * object layer in order to stream to the tar-ball faster.</p>
 */

@Component
public class PkgIconExportArchiveJobRunner extends AbstractJobRunner<PkgIconExportArchiveJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgIconExportArchiveJobRunner.class);

    private static int BATCH_SIZE = 25;

    private static int ROW_PKG_NAME = 0;
    private static int ROW_SIZE = 1;
    private static int ROW_MEDIA_TYPE_CODE = 2;
    private static int ROW_PAYLOAD = 3;
    private static int ROW_PKG_MODIFY_TIMESTAMP = 4;

    @Resource
    private ServerRuntime serverRuntime;

    @Override
    public void run(
            JobService jobService,
            PkgIconExportArchiveJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);

        long startMs = System.currentTimeMillis();
        final ObjectContext context = serverRuntime.getContext();
        int offset = 0;

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.TAR.toString());

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(outputStream);
        ) {

            EJBQLQuery query = new EJBQLQuery(createEjbqlRawRowsExpression());
            query.setFetchLimit(BATCH_SIZE);
            int countLastQuery;

            do {
                query.setFetchOffset(offset);
                List<Object[]> queryResults = context.performQuery(query);
                countLastQuery = queryResults.size();
                appendFromRawRows(tarOutputStream, queryResults);
                offset += countLastQuery;


            } while(countLastQuery > 0);
        }

        LOGGER.info(
                "did produce icon report for {} icons in {}ms",
                offset, System.currentTimeMillis() - startMs);

    }

    private void appendFromRawRows(TarArchiveOutputStream tarArchiveOutputStream, List<Object[]> rows)
            throws IOException {
        for(Object[] row : rows) {
            appendFromRawRow(tarArchiveOutputStream, row);
        }
    }

    private void appendFromRawRow(TarArchiveOutputStream tarArchiveOutputStream, Object[] row)
            throws IOException {
        append(
                tarArchiveOutputStream,
                String.class.cast(row[ROW_PKG_NAME]),
                Number.class.cast(row[ROW_SIZE]),
                String.class.cast(row[ROW_MEDIA_TYPE_CODE]),
                byte[].class.cast(row[ROW_PAYLOAD]),
                Date.class.cast(row[ROW_PKG_MODIFY_TIMESTAMP])
        );
    }

    private void append(
            TarArchiveOutputStream tarArchiveOutputStream,
            String pkgName,
            Number size,
            String mediaTypeCode,
            byte[] payload,
            Date modifyTimestamp) throws IOException {

        String filename = String.join("/", "hdsiconexport",
                pkgName, PkgIcon.deriveFilename(
                        mediaTypeCode, null==size ? null : size.intValue()));

        TarArchiveEntry tarEntry = new TarArchiveEntry(filename);
        tarEntry.setSize(payload.length);
        tarEntry.setModTime((modifyTimestamp.getTime() / 1000) * 1000);
        tarArchiveOutputStream.putArchiveEntry(tarEntry);
        tarArchiveOutputStream.write(payload);
        tarArchiveOutputStream.closeArchiveEntry();
    }

    private String createEjbqlRawRowsExpression() {
        StringBuilder builder = new StringBuilder();

        builder.append("SELECT pii.pkgIcon.pkg.name, pii.pkgIcon.size,\n");
        builder.append("pii.pkgIcon.mediaType.code, pii.data, pii.pkgIcon.pkg.modifyTimestamp\n");
        builder.append("FROM " + PkgIconImage.class.getSimpleName() + " pii\n");
        builder.append("WHERE pii.pkgIcon.pkg.active = true\n");
        builder.append("ORDER BY pii.pkgIcon.pkg.name ASC,\n");
        builder.append("pii.pkgIcon.mediaType.code ASC,\n");
        builder.append("pii.pkgIcon.size ASC\n");

        return builder.toString();
    }

}
