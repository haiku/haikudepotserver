/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.zip.GZIPOutputStream;

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

    public static String PATH_COMPONENT_TOP = "hicn";

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private RuntimeInformationService runtimeInformationService;

    @Resource
    private ObjectMapper objectMapper;

    private DateTimeFormatter dateTimeFormatter = DateTimeHelper.createStandardDateTimeFormat();

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
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream); // tars assumed to be compressed
                final TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(gzipOutputStream)
        ) {

            State state = new State();
            state.tarArchiveOutputStream = tarOutputStream;
            EJBQLQuery query = new EJBQLQuery(createEjbqlRawRowsExpression());
            query.setFetchLimit(BATCH_SIZE);
            int countLastQuery;

            do {
                query.setFetchOffset(offset);
                List<Object[]> queryResults = context.performQuery(query);
                countLastQuery = queryResults.size();
                appendFromRawRows(state, queryResults);
                offset += countLastQuery;
            } while(countLastQuery > 0);

            appendArchiveInfo(state);
        }

        LOGGER.info(
                "did produce icon report for {} icons in {}ms",
                offset, System.currentTimeMillis() - startMs);

    }

    private void appendFromRawRows(State state, List<Object[]> rows)
            throws IOException {
        for(Object[] row : rows) {
            appendFromRawRow(state, row);
        }
    }

    private void appendFromRawRow(State state, Object[] row)
            throws IOException {
        append(
                state,
                String.class.cast(row[ROW_PKG_NAME]),
                Number.class.cast(row[ROW_SIZE]),
                String.class.cast(row[ROW_MEDIA_TYPE_CODE]),
                byte[].class.cast(row[ROW_PAYLOAD]),
                Date.class.cast(row[ROW_PKG_MODIFY_TIMESTAMP])
        );
    }

    private void append(
            State state,
            String pkgName,
            Number size,
            String mediaTypeCode,
            byte[] payload,
            Date modifyTimestamp) throws IOException {

        String filename = String.join("/", PATH_COMPONENT_TOP,
                pkgName, PkgIcon.deriveFilename(
                        mediaTypeCode, null==size ? null : size.intValue()));

        TarArchiveEntry tarEntry = new TarArchiveEntry(filename);
        tarEntry.setSize(payload.length);
        tarEntry.setModTime(roundTimeToSecond(modifyTimestamp));
        state.tarArchiveOutputStream.putArchiveEntry(tarEntry);
        state.tarArchiveOutputStream.write(payload);
        state.tarArchiveOutputStream.closeArchiveEntry();

        if(modifyTimestamp.after(state.latestModifiedTimestamp)) {
            state.latestModifiedTimestamp = modifyTimestamp;
        }
    }

    /**
     * <p>Adds a little informational file into the tar-ball.</p>
     */

    private void appendArchiveInfo(State state) throws IOException {
        byte[] payload = objectMapper.writeValueAsBytes(createArchiveInfo(state));
        TarArchiveEntry tarEntry = new TarArchiveEntry(PATH_COMPONENT_TOP + "/info.json");
        tarEntry.setSize(payload.length);
        tarEntry.setModTime(roundTimeToSecondPlusOne(state.latestModifiedTimestamp));
        state.tarArchiveOutputStream.putArchiveEntry(tarEntry);
        state.tarArchiveOutputStream.write(payload);
        state.tarArchiveOutputStream.closeArchiveEntry();
    }

    private Date roundTimeToSecond(Date date) {
        return new Date((date.getTime() / 1000) * 1000);
    }

    private Date roundTimeToSecondPlusOne(Date date) {
        return new Date(roundTimeToSecond(date).getTime() + 1000);
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

    private ArchiveInfo createArchiveInfo(State state) {
        ArchiveInfo archiveInfo = new ArchiveInfo();
        archiveInfo.agent = "hds";
        archiveInfo.agentVersion = runtimeInformationService.getProjectVersion();
        archiveInfo.createTimestamp = new Date();
        archiveInfo.createTimestampIso = dateTimeFormatter.format(archiveInfo.createTimestamp.toInstant());
        archiveInfo.modifiedTimestamp = roundTimeToSecondPlusOne(state.latestModifiedTimestamp);
        archiveInfo.modifiedTimestampIso = dateTimeFormatter.format(archiveInfo.modifiedTimestamp.toInstant());
        return archiveInfo;
    }

    /**
     * <p>This data gets encoded into the file such that it knows the latest change to the data.</p>
     */

    private final static class ArchiveInfo {

        public Date createTimestamp;
        public String createTimestampIso;
        public Date modifiedTimestamp;
        public String modifiedTimestampIso;
        public String agent;
        public String agentVersion;

    }

    private final static class State {

        TarArchiveOutputStream tarArchiveOutputStream;
        Date latestModifiedTimestamp = new Date(0);

    }

}
