/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.SQLTemplate;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotExportArchiveJobSpecification;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

/**
 * <P>Produces a dump of the screenshots; either for one specific package or for all of the packages.</P>
 */

@Component
public class PkgScreenshotExportArchiveJobRunner
        extends AbstractPkgResourceExportArchiveJobRunner<PkgScreenshotExportArchiveJobSpecification> {

    private static final int BATCH_SIZE = 4; // small because don't want to use too much memory!

    private static final String COLUMN_PKG_NAME = "pkg_name";
    private static final String COLUMN_PAYLOAD = "payload";
    private static final String COLUMN_PKG_MODIFY_TIMESTAMP = "modify_timestamp";
    private static final String COLUMN_ORDERING = "ordering";

    public static final String PATH_COMPONENT_TOP = "hscr";

    public PkgScreenshotExportArchiveJobRunner(
            ServerRuntime serverRuntime,
            RuntimeInformationService runtimeInformationService,
            ObjectMapper objectMapper) {
        super(serverRuntime, runtimeInformationService, objectMapper);
    }

    int getBatchSize() {
        return BATCH_SIZE;
    }

    @Override
    String getPathComponentTop() {
        return PATH_COMPONENT_TOP;
    }

    @Override
    void appendFromRawRow(State state, DataRow row)
            throws IOException {
        append(
                state,
                (String) row.get(COLUMN_PKG_NAME),
                (byte[]) row.get(COLUMN_PAYLOAD),
                (Date) row.get(COLUMN_PKG_MODIFY_TIMESTAMP),
                (Integer) row.get(COLUMN_ORDERING)
        );
    }

    private void append(
            State state,
            String pkgName,
            byte[] payload,
            Date modifyTimestamp,
            Integer ordering) throws IOException {

        Preconditions.checkArgument(StringUtils.isNotBlank(pkgName));
        Preconditions.checkArgument(null != payload && payload.length > 0);
        Preconditions.checkArgument(null != modifyTimestamp);
        Preconditions.checkArgument(null != ordering);

        String filename = String.join("/", PATH_COMPONENT_TOP, pkgName, ordering.toString() + ".png");

        TarArchiveEntry tarEntry = new TarArchiveEntry(filename);
        tarEntry.setSize(payload.length);
        tarEntry.setModTime(roundTimeToSecond(modifyTimestamp));
        state.tarArchiveOutputStream.putArchiveEntry(tarEntry);
        state.tarArchiveOutputStream.write(payload);
        state.tarArchiveOutputStream.closeArchiveEntry();

        if (modifyTimestamp.after(state.latestModifiedTimestamp)) {
            state.latestModifiedTimestamp = modifyTimestamp;
        }
    }

    @Override
    SQLTemplate createQuery(PkgScreenshotExportArchiveJobSpecification specification) {
        SQLTemplate query = (SQLTemplate) serverRuntime.newContext().getEntityResolver()
                .getQueryDescriptor("AllPkgScreenshots").buildQuery();
        if (!Strings.isNullOrEmpty(specification.getPkgName())) {
            query.setParams(Map.of("pkgName", specification.getPkgName()));
        }
        return query;
    }

    Date getLatestModifiedTimestamp(PkgScreenshotExportArchiveJobSpecification specification) {
        if (Strings.isNullOrEmpty(specification.getPkgName())) {
            return PkgSupplement.tryGetLatestModifyTimestamp(serverRuntime.newContext())
                    .orElse(new Date(0L));
        }

        return Pkg.getByName(serverRuntime.newContext(), specification.getPkgName())
                .getPkgSupplement()
                .getLatestPkgModifyTimestampSecondAccuracy();
    }

}
