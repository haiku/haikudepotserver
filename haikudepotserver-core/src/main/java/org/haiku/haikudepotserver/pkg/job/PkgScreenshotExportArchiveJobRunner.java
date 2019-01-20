/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshotImage;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotExportArchiveJobSpecification;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

/**
 * <P>Produces a dump of the screenshots; either for one specific package or for all of the packages.</P>
 */

@Component
public class PkgScreenshotExportArchiveJobRunner
        extends AbstractPkgResourceExportArchiveJobRunner<PkgScreenshotExportArchiveJobSpecification> {

    private static final int BATCH_SIZE = 4; // small because don't want to use too much memory!

    private static final int ROW_PKG_NAME = 0;
    private static final int ROW_PAYLOAD = 1;
    private static final int ROW_PKG_MODIFY_TIMESTAMP = 2;
    private static final int ROW_ORDERING = 3;

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
    void appendFromRawRow(State state, Object[] row)
            throws IOException {
        append(
                state,
                String.class.cast(row[ROW_PKG_NAME]),
                byte[].class.cast(row[ROW_PAYLOAD]),
                Date.class.cast(row[ROW_PKG_MODIFY_TIMESTAMP]),
                Integer.class.cast(row[ROW_ORDERING])
        );
    }

    private void append(
            State state,
            String pkgName,
            byte[] payload,
            Date modifyTimestamp,
            Integer ordering) throws IOException {

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
    EJBQLQuery createEjbqlQuery(PkgScreenshotExportArchiveJobSpecification specification) {
        EJBQLQuery query = new EJBQLQuery(createEjbqlRawRowsExpression(specification));

        if (!Strings.isNullOrEmpty(specification.getPkgName())) {
            query.setParameter("pkgName", specification.getPkgName());
        }

        return query;
    }

    private String createEjbqlRawRowsExpression(PkgScreenshotExportArchiveJobSpecification specification) {
        StringBuilder builder = new StringBuilder();

        builder.append("SELECT psi.pkgScreenshot.pkgSupplement.basePkgName,\n");
        builder.append("psi.data, psi.pkgScreenshot.pkgSupplement.modifyTimestamp,\n");
        builder.append("psi.pkgScreenshot.ordering\n");
        builder.append("FROM " + PkgScreenshotImage.class.getSimpleName() + " psi\n");
        builder.append("WHERE EXISTS\n");
        builder.append("(SELECT p2.name FROM " + Pkg.class.getSimpleName() + " p2 WHERE\n");
        builder.append("p2.pkgSupplement = psi.pkgScreenshot.pkgSupplement AND p2.active = true)\n");

        if (!Strings.isNullOrEmpty(specification.getPkgName())) {
            builder.append("AND psi.pkgScreenshot.pkg.name = :pkgName\n");
        }

        builder.append("ORDER BY psi.pkgScreenshot.pkgSupplement.basePkgName ASC,\n");
        builder.append("psi.pkgScreenshot.ordering ASC\n");

        return builder.toString();
    }

}
