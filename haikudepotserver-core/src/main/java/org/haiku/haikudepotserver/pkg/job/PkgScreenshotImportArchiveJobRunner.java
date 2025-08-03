/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import com.opencsv.CSVWriter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshotImage;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotImportArchiveJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotService;
import org.haiku.haikudepotserver.pkg.model.UserPkgSupplementModificationAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Component
public class PkgScreenshotImportArchiveJobRunner extends AbstractJobRunner<PkgScreenshotImportArchiveJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgScreenshotImportArchiveJobRunner.class);

    private static final HashFunction HASH_FUNCTION = Hashing.sha1();

    private static final Pattern PATTERN_PATH = Pattern.compile("^/?" +
            PkgScreenshotExportArchiveJobRunner.PATH_COMPONENT_TOP +
            "/(" + Pkg.PATTERN_STRING_NAME_CHAR + "+)/([0-9]+)\\.png$");

    // references to the groups in the regex above
    private static final int GROUP_PKGNAME = 1;
    private static final int GROUP_LEAFNAME = 2;

    private static final int CSV_COLUMN_ACTION = 2;
    private static final int CSV_COLUMN_MESSAGE = 3;
    private static final int CSV_COLUMN_CODE = 4;


    private enum Action {
        INVALID,
        ADDED,
        PRESENT,
        NOTFOUND,
        REMOVED
    }

    private final ServerRuntime serverRuntime;
    private final PkgScreenshotService pkgScreenshotService;

    public PkgScreenshotImportArchiveJobRunner(
            ServerRuntime serverRuntime,
            PkgScreenshotService pkgScreenshotService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgScreenshotService = Preconditions.checkNotNull(pkgScreenshotService);
    }

    @Override
    public Class<PkgScreenshotImportArchiveJobSpecification> getSupportedSpecificationClass() {
        return PkgScreenshotImportArchiveJobSpecification.class;
    }

    @Override
    public void run(
            JobService jobService,
            PkgScreenshotImportArchiveJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != specification.getInputDataGuid(), "missing input data guid on specification");
        Preconditions.checkArgument(null != specification.getImportStrategy(), "missing import strategy on specification");
        Preconditions.checkArgument(null != specification.getOwnerUserNickname(), "missing owner user on specification");

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE);

        Optional<JobDataWithByteSource> jobDataWithByteSourceOptional = jobService.tryObtainData(specification.getInputDataGuid());

        if(jobDataWithByteSourceOptional.isEmpty()) {
            throw new IllegalStateException("the job data was not able to be found for guid; " + specification.getInputDataGuid());
        }

        if(!serverRuntime.performInTransaction(() -> {
            try (
                    OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    CSVWriter writer = new CSVWriter(outputStreamWriter)) {

                Map<String, ScreenshotImportMetadatas> metadatas = new HashMap<>();

                writer.writeNext(new String[]{"path", "pkg-name", "action", "message", "code"});

                // sweep through and collect meta-data about the packages in the tar file.
                LOGGER.info("will collect data about packages' screenshots from the archive");
                consumeScreenshotArchiveEntries(
                        jobDataWithByteSourceOptional.get().getByteSource(),
                        (ae) -> collectScreenshotMetadataFromArchive(
                                metadatas,
                                ae.getArchiveInputStream(),
                                ae.getArchiveEntry(),
                                ae.getPkgName(),
                                ae.getOrder()));
                LOGGER.info("did collect data about {} packages' screenshots from the archive", metadatas.size());

                LOGGER.info("will collect data about persisted packages' screenshots");
                collectPersistedScreenshotMetadata(metadatas);
                LOGGER.info("did collect data about persisted packages' screenshots");

                if (specification.getImportStrategy() == PkgScreenshotImportArchiveJobSpecification.ImportStrategy.REPLACE) {
                    LOGGER.info("will delete persisted screenshots that are absent from the archive");
                    int deleted = deletePersistedScreenshotsThatAreNotPresentInArchiveAndReport(
                            specification, writer, metadatas.values());
                    LOGGER.info("did delete {} persisted screenshots that are absent from the archive", deleted);
                }

                blendInArtificialOrderings(metadatas.values());

                // sweep through the archive again and load in those screenshots that are not already present.
                // The ordering of the inbound data should be preserved.
                LOGGER.info("will load screenshots from archive");
                consumeScreenshotArchiveEntries(
                        jobDataWithByteSourceOptional.get().getByteSource(),
                        (ae) -> importScreenshotsFromArchiveAndReport(
                                specification,
                                writer,
                                metadatas.get(ae.getPkgName()),
                                ae.getArchiveInputStream(),
                                ae.getArchiveEntry(),
                                ae.getPkgName(),
                                ae.getOrder()));
                LOGGER.info("did load screenshots from archive");
                return true;
            } catch (IOException e) {
                LOGGER.error("unable to complete the job", e);
            }

            return false;
        })) {
            throw new JobRunnerException("unable to complete job");
        }
    }


    private int consumeScreenshotArchiveEntries(
            ByteSource byteSource,
            Consumer<ArchiveEntryWithPkgNameAndOrdering> archiveEntryConsumer) throws IOException {
        int counter = 0;

        try (
                InputStream inputStream = byteSource.openStream();
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                ArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(gzipInputStream)
        ) {
            ArchiveEntry archiveEntry;

            while (null != (archiveEntry = tarArchiveInputStream.getNextEntry())) {

                Matcher matcher = PATTERN_PATH.matcher(archiveEntry.getName());

                if (matcher.matches()) {
                    archiveEntryConsumer.accept(new ArchiveEntryWithPkgNameAndOrdering(
                            tarArchiveInputStream, archiveEntry,
                            matcher.group(GROUP_PKGNAME), Integer.parseInt(matcher.group(GROUP_LEAFNAME))
                    ));

                    counter++;
                }
            }
        }

        return counter;
    }


    /**
     * if the screenshots from the archive are to replace those already persisted then the old
     * screenshots that are no longer required can be deleted.  Go through those ones persisted
     * and if nothing from the inbound screenshots match then delete it.
     */

    private int deletePersistedScreenshotsThatAreNotPresentInArchiveAndReport(
            PkgScreenshotImportArchiveJobSpecification specification,
            final CSVWriter writer,
            Collection<ScreenshotImportMetadatas> metadatas) {
        return metadatas
                .stream()
                .mapToInt((m) -> deletePersistedScreenshotsThatAreNotPresentInArchiveAndReport(specification, writer, m))
                .sum();
    }

    private int deletePersistedScreenshotsThatAreNotPresentInArchiveAndReport(
            PkgScreenshotImportArchiveJobSpecification specification,
            final CSVWriter writer,
            ScreenshotImportMetadatas metadata) {
        return metadata.getExistingScreenshots()
                .stream()
                .mapToInt((es) -> deletePersistedScreenshotsThatAreNotPresentInArchiveAndReport(
                        specification, writer, metadata, es))
                .sum();
    }

    private int deletePersistedScreenshotsThatAreNotPresentInArchiveAndReport(
            PkgScreenshotImportArchiveJobSpecification specification,
            CSVWriter writer,
            ScreenshotImportMetadatas metadata,
            ExistingScreenshotMetadata existingScreenshot) {
        boolean fromArchiveScreenshotMatches = metadata.getFromArchiveScreenshots()
                .stream()
                .filter((as) -> as.getLength() == existingScreenshot.getLength())
                .anyMatch((as) -> as.getDataHash().equals(existingScreenshot.getDataHash()));

        if(!fromArchiveScreenshotMatches) {
            ObjectContext context = serverRuntime.newContext();
            PkgScreenshot pkgScreenshot = PkgScreenshot.getByCode(context, existingScreenshot.getCode());
            String[] row = new String[] {
                    "",
                    pkgScreenshot.getPkgSupplement().getBasePkgName(),
                    Action.REMOVED.name(),
                    "",
                    pkgScreenshot.getCode()
            };

            pkgScreenshotService.deleteScreenshot(
                    context,
                    new UserPkgSupplementModificationAgent(User.getByNickname(context, specification.getOwnerUserNickname())),
                    pkgScreenshot);

            writer.writeNext(row);
            context.commitChanges(); // job-length txn so won't *actually* be committed here.

            return 1;
        }

        return 0;
    }

    /**
     * <p>Goes through the archive and captures information about each screenshot.</p>
     */

    private void collectScreenshotMetadataFromArchive(
            Map<String, ScreenshotImportMetadatas> data,
            ArchiveInputStream archiveInputStream,
            ArchiveEntry archiveEntry,
            String pkgName,
            int order) {

        ScreenshotImportMetadatas metadatas = data.get(pkgName);

        if (null == metadatas) {
            metadatas = new ScreenshotImportMetadatas();
            ObjectContext context = serverRuntime.newContext();
            Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, pkgName);

            if (pkgOptional.isEmpty()) {
                metadatas.setNotFound();
            }

            data.put(pkgName, metadatas);
        }

        if (!metadatas.isNotFound()) {
            HashingInputStream hashingInputStream = new HashingInputStream(HASH_FUNCTION, archiveInputStream);

            try {
                hashingInputStream.transferTo(ByteStreams.nullOutputStream());
            } catch(IOException ioe) {
                throw new UncheckedIOException(ioe);
            }

            metadatas.add(new FromArchiveScreenshotMetadata(
                    order,
                    archiveEntry.getSize(),
                    hashingInputStream.hash(),
                    archiveEntry.getName()));
        }
    }

    /**
     * <p>Go through the database and collect information about the screenshots that are persisted.</p>
     */

    private void collectPersistedScreenshotMetadata(Map<String, ScreenshotImportMetadatas> data) {
        data.forEach((k, v) -> {
            if (!v.isNotFound()) {
                ObjectContext context = serverRuntime.newContext();
                Pkg pkg = Pkg.getByName(context, k);
                pkg.getPkgSupplement().getPkgScreenshots().forEach((ps) -> v.add(createPersistedScreenshotMetadata(ps)));
            }
        });
    }

    private ExistingScreenshotMetadata createPersistedScreenshotMetadata(PkgScreenshot pkgScreenshot) {
        return new ExistingScreenshotMetadata(
                pkgScreenshot.getOrdering(),
                pkgScreenshot.getLength(),
                pkgScreenshot.getCode());
    }


    /**
     * <p>This method will re-order the inbound screenshots such that they appear in the same order as inbound</p>
     */

    private void blendInArtificialOrderings(Collection<ScreenshotImportMetadatas> metadatas) {
        metadatas.forEach(this::blendInArtificialOrderings);
    }


    private void blendInArtificialOrderings(ScreenshotImportMetadatas metadata) {
        int maxExistingOrderOptional = metadata.getExistingScreenshots()
                .stream()
                .mapToInt(ExistingScreenshotMetadata::getOrder)
                .max()
                .orElse(0) // dummy value
                + 1000;

        metadata.getFromArchiveScreenshots()
                .stream()
                .sorted(Comparator.comparingInt(FromArchiveScreenshotMetadata::getOrder))
                .forEach((fa) -> fa.setDerivedOrder(fa.getOrder() + maxExistingOrderOptional));
    }


    /**
     * <p>If this screenshot coming in from the archive does not exist persisted then load it in.</p>
     */

    private void importScreenshotsFromArchiveAndReport(
            PkgScreenshotImportArchiveJobSpecification specification,
            CSVWriter writer,
            ScreenshotImportMetadatas data,
            ArchiveInputStream archiveInputStream,
            ArchiveEntry archiveEntry,
            String pkgName,
            int order) {

        String[] row = new String[] {
                archiveEntry.getName(), // path
                pkgName, // pkg
                "", // action
                "", // message
                "", // code
        };

        if (data.isNotFound()) {
            row[CSV_COLUMN_ACTION] = Action.NOTFOUND.name();
        } else {
            FromArchiveScreenshotMetadata fromArchiveScreenshotMetadata =
                    data.getFromArchiveScreenshots()
                            .stream()
                            .filter((as) -> as.getLength() == archiveEntry.getSize())
                            .filter((as) -> as.getOrder() == order)
                            .findAny()
                            .orElseThrow(() -> new IllegalStateException("unable to find the from-archive screenshot metadata"));

            Optional<ExistingScreenshotMetadata> existingScreenshotMetadata = data.getExistingScreenshots()
                    .stream()
                    .filter((es) -> archiveEntry.getSize() == es.getLength())
                    .filter((es) -> fromArchiveScreenshotMetadata.getDataHash().equals(es.getDataHash()))
                    .findAny();

            if (existingScreenshotMetadata.isPresent()) {
                row[CSV_COLUMN_ACTION] = Action.PRESENT.name();
                row[CSV_COLUMN_CODE] = existingScreenshotMetadata.get().getCode();
            } else {
                ObjectContext context = serverRuntime.newContext();

                try {
                    PkgScreenshot screenshot = pkgScreenshotService.storePkgScreenshotImage(
                            archiveInputStream,
                            context,
                            new UserPkgSupplementModificationAgent(
                                    User.getByNickname(context, specification.getOwnerUserNickname())),
                            Pkg.getByName(context, pkgName).getPkgSupplement(),
                            fromArchiveScreenshotMetadata.getDerivedOrder());

                    row[CSV_COLUMN_CODE] = screenshot.getCode();
                    row[CSV_COLUMN_ACTION] = Action.ADDED.name();
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } catch (BadPkgScreenshotException e) {
                    row[CSV_COLUMN_ACTION] = Action.INVALID.name();
                    row[CSV_COLUMN_MESSAGE] = e.getMessage();
                }

                context.commitChanges();
            }
        }

        writer.writeNext(row);
    }


    private HashCode generateHashCode(String pkgScreenshotCode) {
        ObjectContext context = serverRuntime.newContext();
        PkgScreenshot pkgScreenshot = PkgScreenshot.getByCode(context, pkgScreenshotCode);
        PkgScreenshotImage pkgScreenshotImage = pkgScreenshot.getPkgScreenshotImage();
        return HASH_FUNCTION.hashBytes(pkgScreenshotImage.getData());
    }


    /**
     * <p>This class captures the details of the entry as well as a couple of elements that are parsed
     * from the path of the archive entry.</p>
     */

    private static class ArchiveEntryWithPkgNameAndOrdering {

        private final ArchiveInputStream archiveInputStream;

        private final ArchiveEntry archiveEntry;

        private final String pkgName;

        private final int order;

        ArchiveEntryWithPkgNameAndOrdering(
                ArchiveInputStream archiveInputStream,
                ArchiveEntry archiveEntry,
                String pkgName,
                int order) {
            this.archiveInputStream = archiveInputStream;
            this.archiveEntry = archiveEntry;
            this.pkgName = pkgName;
            this.order = order;
        }

        ArchiveInputStream getArchiveInputStream() {
            return archiveInputStream;
        }

        ArchiveEntry getArchiveEntry() {
            return archiveEntry;
        }

        public String getPkgName() {
            return pkgName;
        }

        public int getOrder() {
            return order;
        }
    }


    /**
     * <p>This class clumps together data related to a package's screenshots from both what is
     * presently available as well as what is available from the tar-ball.</p>
     */

    private class ScreenshotImportMetadatas {

        private final List<ExistingScreenshotMetadata> existingScreenshots = new ArrayList<>();

        private final List<FromArchiveScreenshotMetadata> fromArchiveScreenshots = new ArrayList<>();

        private boolean notFound = false;


        Collection<ExistingScreenshotMetadata> getExistingScreenshots() {
            return existingScreenshots;
        }

        Collection<FromArchiveScreenshotMetadata> getFromArchiveScreenshots() {
            return fromArchiveScreenshots;
        }

        public void add(ExistingScreenshotMetadata value) {
            existingScreenshots.add(value);
        }

        public void add(FromArchiveScreenshotMetadata value) {
            fromArchiveScreenshots.add(value);
        }

        void setNotFound() {
            notFound = true;
        }

        boolean isNotFound() {
            return notFound;
        }
    }

    /**
     * <p>This model object collects data about each relevant screenshot that is persisted
     * in the database.</p>
     */

    private class ExistingScreenshotMetadata {

        private final int order;

        private final long length;

        private final String code;

        private HashCode dataHash = null;

        ExistingScreenshotMetadata(int order, long length, String code) {
            this.order = order;
            this.length = length;
            this.code = code;
        }

        public int getOrder() {
            return order;
        }

        public long getLength() {
            return length;
        }

        public String getCode() {
            return code;
        }

        HashCode getDataHash() {
            if (null == dataHash) {
                dataHash = generateHashCode(code);
            }

            return dataHash;
        }
    }

    /**
     * <p>This model object collects data about each screenshot from the archive.</p>
     */

    private static class FromArchiveScreenshotMetadata {

        private final int order;

        private int derivedOrder;

        private final long length;

        private final HashCode dataHash;

        private final String path;

        FromArchiveScreenshotMetadata(int order, long length, HashCode dataHash, String path) {
            this.order = order;
            this.length = length;
            this.dataHash = dataHash;
            this.path = path;
        }

        public int getOrder() {
            return order;
        }

        public long getLength() {
            return length;
        }

        HashCode getDataHash() {
            return dataHash;
        }

        public String getPath() {
            return path;
        }

        int getDerivedOrder() {
            return derivedOrder;
        }

        void setDerivedOrder(int derivedOrder) {
            this.derivedOrder = derivedOrder;
        }
    }

}
