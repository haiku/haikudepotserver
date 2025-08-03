/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.SQLTemplate;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.pkg.model.PkgIconExportArchiveJobSpecification;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
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

    private final static String COLUMN_PKG_NAME = "pkg_name";
    private final static String COLUMN_SIZE = "size";
    private final static String COLUMN_MEDIA_TYPE_CODE = "media_type_code";
    private final static String COLUMN_PAYLOAD = "payload";
    private final static String COLUMN_PKG_MODIFY_TIMESTAMP = "modify_timestamp";

    final static String PATH_COMPONENT_TOP = "hicn";

    public PkgIconExportArchiveJobRunner(
            ServerRuntime serverRuntime,
            RuntimeInformationService runtimeInformationService,
            ObjectMapper objectMapper) {
        super(serverRuntime, runtimeInformationService, objectMapper);
    }

    @Override
    public Class<PkgIconExportArchiveJobSpecification> getSupportedSpecificationClass() {
        return PkgIconExportArchiveJobSpecification.class;
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
                (Number) row.get(COLUMN_SIZE),
                (String) row.get(COLUMN_MEDIA_TYPE_CODE),
                (byte[]) row.get(COLUMN_PAYLOAD),
                (Date) row.get(COLUMN_PKG_MODIFY_TIMESTAMP)
        );
    }

    private void append(
            State state,
            String pkgName,
            Number size,
            String mediaTypeCode,
            byte[] payload,
            Date modifyTimestamp) throws IOException {

        Preconditions.checkArgument(StringUtils.isNotBlank(pkgName));
        Preconditions.checkArgument(null == size || size.intValue() > 0);
        Preconditions.checkArgument(StringUtils.isNotBlank(mediaTypeCode));
        Preconditions.checkArgument(null != payload && payload.length > 0);
        Preconditions.checkArgument(null != modifyTimestamp);

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
    SQLTemplate createQuery(PkgIconExportArchiveJobSpecification specification) {
        return (SQLTemplate) serverRuntime.newContext().getEntityResolver()
                .getQueryDescriptor("AllPkgIcons").buildQuery();
    }

    @Override
    Date getLatestModifiedTimestamp(PkgIconExportArchiveJobSpecification specification) {
        return PkgSupplement.getLatestIconModifyTimestamp(serverRuntime.newContext())
                .orElse(new Date(0L));
    }

}
