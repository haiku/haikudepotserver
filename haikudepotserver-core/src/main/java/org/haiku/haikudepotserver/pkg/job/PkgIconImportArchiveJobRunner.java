/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.pkg.model.BadPkgIconException;
import org.haiku.haikudepotserver.pkg.model.PkgIconImportArchiveJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * <p>This importer will take a tar-ball of package icons and will import them into
 * the local database.</p>
 */

@Component
public class PkgIconImportArchiveJobRunner extends AbstractJobRunner<PkgIconImportArchiveJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgIconImportArchiveJobRunner.class);

    private static final Pattern PATTERN_PATH = Pattern.compile("^/?" +
            PkgIconExportArchiveJobRunner.PATH_COMPONENT_TOP +
            "/(" + Pkg.PATTERN_STRING_NAME_CHAR + "+)/[A-Za-z0-9_-]+\\.([A-Za-z0-9]+)$");

    // references to the groups in the regex above
    private static final int GROUP_PKGNAME = 1;
    private static final int GROUP_LEAFEXTENSION = 2;

    private static final long MAX_ICON_PAYLOAD = 128 * 1024; // 128k

    private enum Action {
        INVALID,
        UPDATED,
        NOTFOUND
    }

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Override
    public void run(
            JobService jobService,
            PkgIconImportArchiveJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != specification.getInputDataGuid(), "missing imput data guid on specification");

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString());

        Optional<JobDataWithByteSource> jobDataWithByteSourceOptional = jobService.tryObtainData(specification.getInputDataGuid());

        if(!jobDataWithByteSourceOptional.isPresent()) {
            throw new IllegalStateException("the job data was not able to be found for guid; " + specification.getInputDataGuid());
        }

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter, ',');
                InputStream inputStream = jobDataWithByteSourceOptional.get().getByteSource().openStream();
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipInputStream);
        ) {

            String[] headings = new String[] { "path", "action", "message" };
            writer.writeNext(headings);
            processEntriesFromArchive(tarArchiveInputStream, writer);
        }

    }

    private void processEntriesFromArchive(ArchiveInputStream archiveInputStream, CSVWriter writer) throws IOException {
        String[] row = new String[3];

        ArchiveEntry archiveEntry;

        while(null != (archiveEntry = archiveInputStream.getNextEntry())) {
            if (!archiveEntry.isDirectory()) {
                Matcher nameMatcher = PATTERN_PATH.matcher(archiveEntry.getName());
                ArchiveEntryResult result;
                row[0] = archiveEntry.getName();

                if(nameMatcher.matches()) {

                    if(archiveEntry.getSize() <= MAX_ICON_PAYLOAD) {
                        result = processMatchingFileEntryFromArchive(
                                archiveInputStream, archiveEntry,
                                nameMatcher.group(GROUP_PKGNAME),
                                nameMatcher.group(GROUP_LEAFEXTENSION).toLowerCase());
                    } else {
                        result = new ArchiveEntryResult(
                                Action.INVALID,
                                "ignoring archive entry as the payload was too long"
                        );
                    }
                } else {
                    result = new ArchiveEntryResult(
                            Action.INVALID,
                            "ignoring archive entry as the form of the name is invalid"
                    );
                }

                row[1] = result.action.name();
                row[2] = StringUtils.trimToEmpty(result.message);
                writer.writeNext(row);

            } else {
                LOGGER.debug("ignoring directory from archive; [{}]", archiveEntry.getName());
            }
        }

    }

    private ArchiveEntryResult processMatchingFileEntryFromArchive(
            ArchiveInputStream archiveInputStream,
            ArchiveEntry archiveEntry,
            String pkgName, String leafnameExtension)
    throws IOException {

        ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, pkgName);

        if(pkgOptional.isPresent()) {

            Optional<org.haiku.haikudepotserver.dataobjects.MediaType> mediaType =
                    org.haiku.haikudepotserver.dataobjects.MediaType.getByExtension(context, leafnameExtension);

            if(!mediaType.isPresent()) {
                return new ArchiveEntryResult(Action.INVALID, "unknown file-extension");
            }

            switch(mediaType.get().getCode()) {

                case org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE:
                case org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_PNG:
                    break;

                default:
                    return new ArchiveEntryResult(Action.INVALID, "bad media type for icon");
            }

            try {
                pkgOrchestrationService.storePkgIconImage(
                        archiveInputStream,
                        mediaType.get(),
                        null, // there is no expected icon size
                        context,
                        pkgOptional.get());
            } catch (BadPkgIconException e) {
                return new ArchiveEntryResult(Action.INVALID, e.getMessage());
            }

        } else {
            LOGGER.info("pkg not found; [{}]", pkgName);
            return new ArchiveEntryResult(Action.NOTFOUND, "unable to find the associated pkg");
        }

        context.commitChanges();

        return new ArchiveEntryResult(Action.UPDATED, null);
    }

    private static class ArchiveEntryResult {

        Action action;
        String message;

        ArchiveEntryResult(Action action, String message) {
            this.action = action;
            this.message = message;
        }

    }


}
