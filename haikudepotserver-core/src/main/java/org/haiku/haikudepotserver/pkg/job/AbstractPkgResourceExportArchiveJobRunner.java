/*
 * Copyright 2016-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.haiku.haikudepotserver.support.ArchiveInfo;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

abstract class AbstractPkgResourceExportArchiveJobRunner<T extends JobSpecification> extends AbstractJobRunner<T> {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractPkgResourceExportArchiveJobRunner.class);

    @Resource
    protected ServerRuntime serverRuntime;

    @Resource
    protected RuntimeInformationService runtimeInformationService;

    @Resource
    protected ObjectMapper objectMapper;

    @Override
    public void run(
            JobService jobService,
            T specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);

        Stopwatch stopwatch = Stopwatch.createStarted();
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
            EJBQLQuery query = createEjbqlQuery(specification);
            query.setFetchLimit(getBatchSize());
            int countLastQuery;

            do {
                query.setFetchOffset(offset);
                List<Object[]> queryResults = context.performQuery(query);
                countLastQuery = queryResults.size();
                appendFromRawRows(state, queryResults);
                offset += countLastQuery;

                if (0 == offset % 100) {
                    LOGGER.info("processed {} entries", offset + 1);
                }

            } while(countLastQuery > 0);

            appendArchiveInfo(state);
        }

        LOGGER.info("did produce report for {} entries in {}ms", offset, stopwatch.elapsed(TimeUnit.MILLISECONDS));

    }

    abstract int getBatchSize();

    abstract EJBQLQuery createEjbqlQuery(T specification);

    abstract String getPathComponentTop();

    void appendFromRawRows(State state, List<Object[]> rows)
            throws IOException {
        for(Object[] row : rows) {
            appendFromRawRow(state, row);
        }
    }

    abstract void appendFromRawRow(State state, Object[] row) throws IOException;

    /**
     * <p>Adds a little informational file into the tar-ball.</p>
     */

    void appendArchiveInfo(State state) throws IOException {
        ArchiveInfo archiveInfo = new ArchiveInfo(
                DateTimeHelper.secondAccuracyDatePlusOneSecond(state.latestModifiedTimestamp),
                runtimeInformationService.getProjectVersion());

        byte[] payload = objectMapper.writeValueAsBytes(archiveInfo);
        TarArchiveEntry tarEntry = new TarArchiveEntry(getPathComponentTop() + "/info.json");
        tarEntry.setSize(payload.length);
        tarEntry.setModTime(roundTimeToSecondPlusOne(state.latestModifiedTimestamp));
        state.tarArchiveOutputStream.putArchiveEntry(tarEntry);
        state.tarArchiveOutputStream.write(payload);
        state.tarArchiveOutputStream.closeArchiveEntry();
    }

    Date roundTimeToSecond(Date date) {
        return new Date((date.getTime() / 1000) * 1000);
    }

    private Date roundTimeToSecondPlusOne(Date date) {
        return new Date(roundTimeToSecond(date).getTime() + 1000);
    }

    final static class State {

        TarArchiveOutputStream tarArchiveOutputStream;
        Date latestModifiedTimestamp = new Date(0);

    }

}
