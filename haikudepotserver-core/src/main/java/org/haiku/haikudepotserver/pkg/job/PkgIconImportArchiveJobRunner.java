/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import com.opencsv.CSVWriter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.BadPkgIconException;
import org.haiku.haikudepotserver.pkg.model.PkgIconImportArchiveJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.pkg.model.UserPkgSupplementModificationAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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

    private static final Pattern PATTERN_PKG_PATH = Pattern.compile("^/?" +
            PkgIconExportArchiveJobRunner.PATH_COMPONENT_TOP +
            "/(" + Pkg.PATTERN_STRING_NAME_CHAR + "+)(/.+)?$");

    // references to the groups in the regex above
    private static final int GROUP_PKGNAME = 1;
    private static final int GROUP_LEAFEXTENSION = 2;

    private static final int CSV_COLUMN_PKGNAME = 0;
    private static final int CSV_COLUMN_ACTION = 1;
    private static final int CSV_COLUMN_MESSAGE = 2;

    private static final long MAX_ICON_PAYLOAD = 128 * 1024; // 128k

    private enum Action {
        INVALID,
        UPDATED,
        NOTFOUND,
        REMOVED
    }

    private final ServerRuntime serverRuntime;
    private final PkgIconService pkgIconService;

    public PkgIconImportArchiveJobRunner(
            ServerRuntime serverRuntime,
            PkgIconService pkgIconService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgIconService = Preconditions.checkNotNull(pkgIconService);
    }

    @Override
    public void run(
            JobService jobService,
            PkgIconImportArchiveJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != specification.getInputDataGuid(), "missing input data guid on specification");
        Preconditions.checkArgument(null != specification.getOwnerUserNickname(), "the owner nickname is required");

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString());

        Optional<JobDataWithByteSource> jobDataWithByteSourceOptional = jobService.tryObtainData(specification.getInputDataGuid());

        if(jobDataWithByteSourceOptional.isEmpty()) {
            throw new IllegalStateException("the job data was not able to be found for guid; " + specification.getInputDataGuid());
        }

        if(!serverRuntime.performInTransaction(() -> {

            try (
                    OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    CSVWriter writer = new CSVWriter(outputStreamWriter)) {

                String[] headings = new String[]{"path", "action", "message"};
                writer.writeNext(headings);

                // make a first sweep to delete all existing icons for packages in the spreadsheet.

                try (
                        InputStream inputStream = jobDataWithByteSourceOptional.get().getByteSource().openStream();
                        GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                        TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipInputStream)
                ) {
                    clearPackagesIconsAppearingInArchive(specification, tarArchiveInputStream, writer);
                }

                // now load the icons in.

                try (
                        InputStream inputStream = jobDataWithByteSourceOptional.get().getByteSource().openStream();
                        GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                        TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipInputStream)
                ) {
                    processEntriesFromArchive(specification, tarArchiveInputStream, writer);
                }

                return true;
            } catch (IOException e) {
                LOGGER.error("unable to complete job; ", e);
            }

            return false;
        })) {
            throw new JobRunnerException("unable to complete job");
        }
    }

    /**
     * <p>All packages appearing in the archive should first have their icons removed.</p>
     */

    private void clearPackagesIconsAppearingInArchive(
            PkgIconImportArchiveJobSpecification specification,
            ArchiveInputStream archiveInputStream,
            CSVWriter writer) throws IOException {

        String[] row = new String[3];
        Set<String> pkgNamesProcessed = new HashSet<>();
        ArchiveEntry archiveEntry;
        row[CSV_COLUMN_MESSAGE] = "";

        while(null != (archiveEntry = archiveInputStream.getNextEntry())) {
            Matcher matcher = PATTERN_PKG_PATH.matcher(archiveEntry.getName());

            if (matcher.matches()) {
                String pkgName = matcher.group(GROUP_PKGNAME);

                if (!pkgNamesProcessed.contains(pkgName)) {
                    row[CSV_COLUMN_PKGNAME] = archiveEntry.getName();
                    ObjectContext context = serverRuntime.newContext();
                    Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, pkgName);

                    if (pkgOptional.isPresent()) {
                        User user = User.getByNickname(context, specification.getOwnerUserNickname());
                        pkgIconService.removePkgIcon(
                                context,
                                new UserPkgSupplementModificationAgent(user),
                                pkgOptional.get().getPkgSupplement());
                        LOGGER.info("removed icons for pkg; {}", pkgName);
                        row[CSV_COLUMN_ACTION] = Action.REMOVED.name();
                    } else {
                        LOGGER.info("not able to find pkg; {}", pkgName);
                        row[CSV_COLUMN_ACTION] = Action.NOTFOUND.name();
                    }

                    pkgNamesProcessed.add(pkgName);
                    writer.writeNext(row);
                }
            }
        }
    }

    private void processEntriesFromArchive(
            PkgIconImportArchiveJobSpecification specification,
            ArchiveInputStream archiveInputStream,
            CSVWriter writer) throws IOException {
        String[] row = new String[3];

        ArchiveEntry archiveEntry;

        while (null != (archiveEntry = archiveInputStream.getNextEntry())) {
            if (!archiveEntry.isDirectory()) {
                Matcher nameMatcher = PATTERN_PATH.matcher(archiveEntry.getName());
                ArchiveEntryResult result;
                row[CSV_COLUMN_PKGNAME] = archiveEntry.getName();

                if (nameMatcher.matches()) {

                    if (archiveEntry.getSize() <= MAX_ICON_PAYLOAD) {
                        result = processMatchingFileEntryFromArchive(
                                specification,
                                archiveInputStream,
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

                row[CSV_COLUMN_ACTION] = result.action.name();
                row[CSV_COLUMN_MESSAGE] = StringUtils.trimToEmpty(result.message);
                writer.writeNext(row);

            } else {
                LOGGER.debug("ignoring directory from archive; [{}]", archiveEntry.getName());
            }
        }

    }

    private ArchiveEntryResult processMatchingFileEntryFromArchive(
            PkgIconImportArchiveJobSpecification specification,
            ArchiveInputStream archiveInputStream,
            String pkgName, String leafnameExtension)
            throws IOException {

        ObjectContext context = serverRuntime.newContext();
        User user = User.getByNickname(context, specification.getOwnerUserNickname());
        Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, pkgName);

        if (pkgOptional.isPresent()) {

            Optional<org.haiku.haikudepotserver.dataobjects.MediaType> mediaType =
                    org.haiku.haikudepotserver.dataobjects.MediaType.tryGetByExtension(context, leafnameExtension);

            if (mediaType.isEmpty()) {
                return new ArchiveEntryResult(Action.INVALID, "unknown file-extension");
            }

            switch (mediaType.get().getCode()) {

                case org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE:
                case org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_PNG:
                    break;

                default:
                    return new ArchiveEntryResult(Action.INVALID, "bad media type for icon");
            }

            try {
                pkgIconService.storePkgIconImage(
                        archiveInputStream,
                        mediaType.get(),
                        null, // there is no expected icon size
                        context,
                        new UserPkgSupplementModificationAgent(user),
                        pkgOptional.get().getPkgSupplement());
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

    /**
     * <p>This object models the result of having processed an icon-loading.</p>
     */

    private static class ArchiveEntryResult {

        Action action;
        String message;

        ArchiveEntryResult(Action action, String message) {
            this.action = action;
            this.message = message;
        }

    }

}
