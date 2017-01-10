/*
 * Copyright 2015-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import org.apache.cayenne.query.EJBQLQuery;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgIconImage;
import org.haiku.haikudepotserver.pkg.model.PkgIconExportArchiveJobSpecification;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

/**
 * <p>Produce a tar-ball file containing all of the icons of the packages.  This uses a direct query avoiding the
 * object layer in order to stream to the tar-ball faster.</p>
 */

@Component
public class PkgIconExportArchiveJobRunner extends AbstractPkgResourceExportArchiveJobRunner<PkgIconExportArchiveJobSpecification> {

    private final static int BATCH_SIZE = 25;

    private final static int ROW_PKG_NAME = 0;
    private final static int ROW_SIZE = 1;
    private final static int ROW_MEDIA_TYPE_CODE = 2;
    private final static int ROW_PAYLOAD = 3;
    private final static int ROW_PKG_MODIFY_TIMESTAMP = 4;

    public final static String PATH_COMPONENT_TOP = "hicn";

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

    @Override
    EJBQLQuery createEjbqlQuery(PkgIconExportArchiveJobSpecification specification) {
        return new EJBQLQuery(createEjbqlRawRowsExpression());
    }

    private String createEjbqlRawRowsExpression() {
        return String.join("\n",
                "SELECT pii.pkgIcon.pkg.name, pii.pkgIcon.size,",
                "pii.pkgIcon.mediaType.code, pii.data, pii.pkgIcon.pkg.iconModifyTimestamp",
                "FROM " + PkgIconImage.class.getSimpleName() + " pii",
                "WHERE pii.pkgIcon.pkg.active = true",
                "ORDER BY pii.pkgIcon.pkg.name ASC,",
                "pii.pkgIcon.mediaType.code ASC,",
                "pii.pkgIcon.size ASC"
        );
    }

}
