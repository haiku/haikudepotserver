/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.model.BadPkgIconException;
import org.haikuos.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.pkg.model.SizeLimitReachedException;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.ImageHelper;
import org.haikuos.haikudepotserver.support.cayenne.LikeHelper;
import org.imgscalr.Scalr;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * <p>This service undertakes non-trivial operations on packages.</p>
 */

@Service
public class PkgOrchestrationService {

    protected static Logger logger = LoggerFactory.getLogger(PkgOrchestrationService.class);

    protected static int SCREENSHOT_SIDE_LIMIT = 1500;

    // these seem like reasonable limits for the size of image data to have to
    // handle in-memory.

    protected static int SCREENSHOT_SIZE_LIMIT = 2 * 1024 * 1024; // 2MB
    protected static int ICON_SIZE_LIMIT = 100 * 1024; // 100k

    private ImageHelper imageHelper = new ImageHelper();

    @Resource
    DataSource dataSource;

    // ------------------------------
    // HELP

    /**
     * <p>This method will read in the quantity of bytes from the input stream upto the limit.  If the limit is
     * reached, the method will throw {@link org.haikuos.haikudepotserver.pkg.model.SizeLimitReachedException}.</p>
     */

    public static byte[] toByteArray(InputStream inputStream, int sizeLimit) throws IOException {
        Preconditions.checkNotNull(inputStream);
        Preconditions.checkState(sizeLimit > 0);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8*1024];
        int read;

        while(-1 != (read = inputStream.read(buffer,0,buffer.length))) {

            if(read + baos.size() > sizeLimit) {
                throw new SizeLimitReachedException();
            }

            baos.write(buffer,0,read);
        }

        return baos.toByteArray();
    }

    // ------------------------------
    // SEARCH

    /**
     * <p>This performs a search on the packages.  Note that the prefetch tree node that is supplied is relative to
     * the package version.</p>
     */

    public List<PkgVersion> search(ObjectContext context, PkgSearchSpecification search, PrefetchTreeNode prefetchTreeNode) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);
        Preconditions.checkNotNull(search.getArchitecture());
        Preconditions.checkState(null==search.getDaysSinceLatestVersion() || search.getDaysSinceLatestVersion().intValue() > 0);

        if(null!=search.getPkgNames() && search.getPkgNames().isEmpty()) {
            return Collections.emptyList();
        }

        // unfortunately this one became too complex to get working properly in JPQL; had to resort
        // to using raw SQL.

        StringBuilder queryBuilder = new StringBuilder();
        List<Object> parameters = Lists.newArrayList();

        {
            queryBuilder.append("SELECT pv.id FROM haikudepot.pkg p");
            queryBuilder.append(" JOIN haikudepot.pkg_version pv ON pv.pkg_id = p.id");
            queryBuilder.append(" JOIN haikudepot.architecture a ON pv.architecture_id = a.id");

            if(null!=search.getPkgCategory()) {
                queryBuilder.append(" JOIN haikudepot.pkg_pkg_category ppc ON p.id = ppc.pkg_id");
                queryBuilder.append(" JOIN haikudepot.pkg_category pc ON pc.id=ppc.pkg_category_id");
            }

            queryBuilder.append(" WHERE");

            // make sure that we are hitting the architecture that we want.

            queryBuilder.append(" (a.code = ? OR a.code = ?)");
            parameters.add(search.getArchitecture().getCode());
            parameters.add(Architecture.CODE_ANY);

            // make sure that we are dealing only with active packages.

            if(!search.getIncludeInactive()) {
                queryBuilder.append(" AND p.active = true");
                queryBuilder.append(" AND pv.active = true");
            }

            if(null!=search.getDaysSinceLatestVersion()) {
                queryBuilder.append(" AND pv.create_timestamp > ?");
                parameters.add(new java.sql.Timestamp(DateTime.now().minusDays(search.getDaysSinceLatestVersion().intValue()).getMillis()));
            }

            if(!Strings.isNullOrEmpty(search.getExpression())) {
                queryBuilder.append(" AND p.name LIKE ?");
                parameters.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
            }

            if(null!=search.getPkgCategory()) {
                queryBuilder.append(" AND pc.code = ?");
                parameters.add(search.getPkgCategory().getCode());
            }

            if(null!=search.getPkgNames()) {
                List<String> pn = search.getPkgNames();

                queryBuilder.append(" AND p.name IN (");

                for(int i=0;i<pn.size();i++) {
                    if(0!=i) {
                        queryBuilder.append(',');
                    }
                    queryBuilder.append('?');
                    parameters.add(pn.get(i));
                }

                queryBuilder.append(")");
            }

            // make sure that we are dealing with the latest version in the package.

            queryBuilder.append(" AND pv.id = (");
            queryBuilder.append(" SELECT pv2.id FROM haikudepot.pkg_version pv2 WHERE");
            queryBuilder.append(" pv2.pkg_id = pv.pkg_id");
            queryBuilder.append(" AND pv2.architecture_id = pv.architecture_id");

            if(!search.getIncludeInactive()) {
                queryBuilder.append(" AND pv2.active = true");
            }

            queryBuilder.append(" ORDER BY");
            queryBuilder.append(" pv2.major DESC NULLS LAST");
            queryBuilder.append(" ,pv2.minor DESC NULLS LAST");
            queryBuilder.append(" ,pv2.micro DESC NULLS LAST");
            queryBuilder.append(" ,pv2.pre_release DESC NULLS LAST");
            queryBuilder.append(" ,pv2.revision DESC NULLS LAST");
            queryBuilder.append(" LIMIT 1");
            queryBuilder.append(")");

            if(null!=search.getSortOrdering()) {
                queryBuilder.append(" ORDER BY");

                switch (search.getSortOrdering()) {

                    case VERSIONVIEWCOUNTER:
                        queryBuilder.append(" pv.view_counter DESC, p.name ASC");
                        break;

                    case VERSIONCREATETIMESTAMP:
                        queryBuilder.append(" pv.create_timestamp DESC");
                        break;

                    case NAME:
                        queryBuilder.append(" p.name ASC");
                        break;

                    default:
                        throw new IllegalStateException("unhandled sort ordering; " + search.getSortOrdering());

                }
            }

            queryBuilder.append(" LIMIT ?");
            parameters.add(search.getLimit());

            queryBuilder.append(" OFFSET ?");
            parameters.add(search.getOffset());
        }

        // now run the raw database query to get the primary keys and then we will haul in the data using Cayenne
        // as a separate query which should be quite fast.

        final List<Long> pkgVersionIds = Lists.newArrayList();

        {
            PreparedStatement preparedStatement = null;
            ResultSet resultSet = null;
            Connection connection = null;

            try {
                connection = dataSource.getConnection();
                connection.setAutoCommit(false);

                preparedStatement = connection.prepareStatement(queryBuilder.toString());

                for(int i=0;i<parameters.size();i++) {
                    preparedStatement.setObject(i+1, parameters.get(i));
                }

                resultSet = preparedStatement.executeQuery();

                while(resultSet.next()) {
                    pkgVersionIds.add(resultSet.getLong(1));
                }
            }
            catch(SQLException se) {
                throw new RuntimeException("unable to search the packages from the search specification", se);
            }
            finally {
                Closeables.closeQuietly(resultSet);
                Closeables.closeQuietly(preparedStatement);
                Closeables.closeQuietly(connection);
            }
        }

        // now get the actual data objects which might be in the wrong order.  Use a pre-fetch here to pick up the
        // package as well.  We do this to avoid the extra fault that will come in producing the output.

        SelectQuery query = new SelectQuery(
                PkgVersion.class,
                ExpressionFactory.inDbExp(PkgVersion.ID_PK_COLUMN, pkgVersionIds));

        if(null==prefetchTreeNode) {
            prefetchTreeNode = new PrefetchTreeNode();
        }

        // we always want to get the package for a given version
        prefetchTreeNode.addPath(PkgVersion.PKG_PROPERTY);

        query.setPrefetchTree(prefetchTreeNode);

        List<PkgVersion> pkgVersions = context.performQuery(query);

        // repeat the sort of the main query to get the packages back into order again.

        Collections.sort(pkgVersions, new Comparator<PkgVersion>() {
            @Override
            public int compare(PkgVersion o1, PkgVersion o2) {
                Long i1 = (Long) o1.getObjectId().getIdSnapshot().get(PkgVersion.ID_PK_COLUMN);
                Long i2 = (Long) o2.getObjectId().getIdSnapshot().get(PkgVersion.ID_PK_COLUMN);
                int offset1 = pkgVersionIds.indexOf(i1);
                int offset2 = pkgVersionIds.indexOf(i2);

                if(-1==offset1 || -1==offset2) {
                    throw new IllegalStateException("a pkg version being sorted was not able to be found in the original list of id-s to be fetched");
                }

                return Integer.compare(offset1,offset2);
            }
        });

        return pkgVersions;
    }

    // ------------------------------
    // ICONS

    /**
     * <p>This will output a bitmap image for a generic icon.</p>
     */

    public void writeGenericIconImage(
            OutputStream output,
            int size) throws IOException {

        Preconditions.checkNotNull(output);
        Preconditions.checkState(16==size||32==size);

        String resource = String.format("/img/generic/generic%d.png", size);
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream(resource);

            if(null==inputStream) {
                throw new IllegalStateException(String.format("the resource; %s was not able to be found, but should be in the application build product", resource));
            }
            else {
                ByteStreams.copy(inputStream, output);
            }
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    /**
     * <p>This method will write the icon data supplied in the input to the package as its icon.  Note that the icon
     * must comply with necessary characteristics; for example it must be either 16 or 32 pixels along both its sides
     * if it is a PNG.  If it is non-compliant then an instance of
     * {@link org.haikuos.haikudepotserver.pkg.model.BadPkgIconException} will be thrown.</p>
     */

    public PkgIcon storePkgIconImage(
            InputStream input,
            MediaType mediaType,
            Integer expectedSize,
            ObjectContext context,
            Pkg pkg) throws IOException, BadPkgIconException {

        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(mediaType);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);

        byte[] imageData = toByteArray(input, ICON_SIZE_LIMIT);
        Optional<PkgIcon> pkgIconOptional = null;
        Integer size = null;

        if(com.google.common.net.MediaType.PNG.toString().equals(mediaType.getCode())) {

            ImageHelper.Size pngSize =  imageHelper.derivePngSize(imageData);

            if(null==pngSize) {
                logger.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it is not a valid png image",pkg.getName());
                throw new BadPkgIconException();
            }

            if(!pngSize.areSides(16) && !pngSize.areSides(32)) {
                logger.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it must be either 32x32 or 16x16 px, but was {}",pkg.getName(),pngSize.toString());
                throw new BadPkgIconException();
            }

            if(null!=expectedSize && !pngSize.areSides(expectedSize)) {
                logger.warn("attempt to set the bitmap (png) package icon for package {}, but the size did not match the expected size",pkg.getName());
                throw new BadPkgIconException();
            }

            size = pngSize.width;
            pkgIconOptional = pkg.getPkgIcon(mediaType, pngSize.width);
        }
        else {
            if(MediaType.MEDIATYPE_HAIKUVECTORICONFILE.equals(mediaType.getCode())) {
                if(!imageHelper.looksLikeHaikuVectorIconFormat(imageData)) {
                    logger.warn("attempt to set the vector (hvif) package icon for package {}, but the data does not look like hvif",pkg.getName());
                    throw new BadPkgIconException();
                }
                pkgIconOptional = pkg.getPkgIcon(mediaType, null);
            }
            else {
                throw new IllegalStateException("unhandled media type; "+mediaType.getCode());
            }
        }

        PkgIconImage pkgIconImage = null;

        if(pkgIconOptional.isPresent()) {
            pkgIconImage = pkgIconOptional.get().getPkgIconImage().get();
        }
        else {
            PkgIcon pkgIcon = context.newObject(PkgIcon.class);
            pkg.addToManyTarget(Pkg.PKG_ICONS_PROPERTY, pkgIcon, true);
            pkgIcon.setMediaType(mediaType);
            pkgIcon.setSize(size);
            pkgIconImage = context.newObject(PkgIconImage.class);
            pkgIcon.addToManyTarget(PkgIcon.PKG_ICON_IMAGES_PROPERTY, pkgIconImage, true);
            pkgIconOptional = Optional.of(pkgIcon);
        }

        pkgIconImage.setData(imageData);
        pkg.setModifyTimestamp(new java.util.Date());

        if(null!=size) {
            logger.info("the icon {}px for package {} has been updated", size, pkg.getName());
        }
        else {
            logger.info("the icon for package {} has been updated", pkg.getName());
        }

        return pkgIconOptional.get();
    }

    // ------------------------------
    // SCREENSHOT

    /**
     * <p>This method will write the package's screenshot to the output stream.  It will constrain the output to the
     * size given by scaling the image.  The output is a PNG image.</p>
     */

    public void writePkgScreenshotImage(
            OutputStream output,
            ObjectContext context,
            PkgScreenshot screenshot,
            int targetWidth,
            int targetHeight) throws IOException {

        Preconditions.checkNotNull(output);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(screenshot);
        Preconditions.checkState(targetHeight > 0);
        Preconditions.checkState(targetWidth > 0);

        Optional<PkgScreenshotImage> pkgScreenshotImageOptional = screenshot.getPkgScreenshotImage();

        if(!pkgScreenshotImageOptional.isPresent()) {
            throw new IllegalStateException("the screenshot "+screenshot.getCode()+" is missing a screenshot image");
        }

        if(!pkgScreenshotImageOptional.get().getMediaType().getCode().equals(com.google.common.net.MediaType.PNG.toString())) {
            throw new IllegalStateException("the screenshot system only supports png images at the present time");
        }

        byte[] data = pkgScreenshotImageOptional.get().getData();
        ImageHelper.Size size = imageHelper.derivePngSize(data);

        // check to see if the screenshot needs to be resized to fit.
        if(size.width > targetWidth || size.height > targetHeight) {
            ByteArrayInputStream imageInputStream = new ByteArrayInputStream(data);
            BufferedImage bufferedImage = ImageIO.read(imageInputStream);
            BufferedImage scaledBufferedImage = Scalr.resize(bufferedImage, targetWidth, targetHeight);
            ImageIO.write(scaledBufferedImage, "png", output);
        }
        else {
            output.write(data);
        }
    }

    /**
     * <p>This method will write the PNG data supplied in the input to the package as a screenshot.  Note that the icon
     * must comply with necessary characteristics.  If it is not compliant then an images of
     * {@link org.haikuos.haikudepotserver.pkg.model.BadPkgScreenshotException} will be thrown.</p>
     */

    public PkgScreenshot storePkgScreenshotImage(
            InputStream input,
            ObjectContext context,
            Pkg pkg) throws IOException, BadPkgScreenshotException {

        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);

        byte[] pngData = toByteArray(input, SCREENSHOT_SIZE_LIMIT);
        ImageHelper.Size size =  imageHelper.derivePngSize(pngData);

        if(null==size) {
            logger.warn("attempt to store a screenshot image that is not a png");
            throw new BadPkgScreenshotException();
        }

        // check that the file roughly looks like PNG and the size is something
        // reasonable.

        if(size.height > SCREENSHOT_SIDE_LIMIT || size.width > SCREENSHOT_SIDE_LIMIT) {
            logger.warn("attempt to store a screenshot image that is too large; "+size.toString());
            throw new BadPkgScreenshotException();
        }

        MediaType png = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get();

        // now we need to know the largest ordering so we can add this one at the end of the orderings
        // such that it is the next one in the list.

        int ordering = 1;
        Optional<Integer> highestExistingScreenshotOrdering = pkg.getHighestPkgScreenshotOrdering();

        if(highestExistingScreenshotOrdering.isPresent()) {
            ordering = highestExistingScreenshotOrdering.get().intValue() + 1;
        }

        PkgScreenshot screenshot = context.newObject(PkgScreenshot.class);
        screenshot.setCode(UUID.randomUUID().toString());
        screenshot.setOrdering(new Integer(ordering));
        screenshot.setHeight(size.height);
        screenshot.setWidth(size.width);
        screenshot.setLength(pngData.length);
        pkg.addToManyTarget(Pkg.PKG_SCREENSHOTS_PROPERTY, screenshot, true);

        PkgScreenshotImage screenshotImage = context.newObject(PkgScreenshotImage.class);
        screenshotImage.setMediaType(png);
        screenshotImage.setData(pngData);
        screenshot.addToManyTarget(PkgScreenshot.PKG_SCREENSHOT_IMAGES_PROPERTY, screenshotImage, true);

        pkg.setModifyTimestamp(new java.util.Date());

        logger.info("a screenshot #{} has been added to package {} ({})", ordering, pkg.getName(), screenshot.getCode());

        return screenshot;
    }

    // ------------------------------
    // IMPORT

    private Expression toExpression(org.haikuos.pkg.model.PkgVersion version) {
        return ExpressionFactory.matchExp(
                org.haikuos.haikudepotserver.dataobjects.PkgVersion.MAJOR_PROPERTY, version.getMajor())
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.dataobjects.PkgVersion.MINOR_PROPERTY, version.getMinor()))
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.dataobjects.PkgVersion.MICRO_PROPERTY, version.getMicro()))
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.dataobjects.PkgVersion.PRE_RELEASE_PROPERTY, version.getPreRelease()))
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.dataobjects.PkgVersion.REVISION_PROPERTY, version.getRevision()));
    }

    /**
     * <p>This method will import the package described by the {@paramref pkg} parameter by locating the package and
     * either creating it or updating it as necessary.</p>
     */

    public void importFrom(
            ObjectContext objectContext,
            ObjectId repositoryObjectId,
            org.haikuos.pkg.model.Pkg pkg) {

        Preconditions.checkNotNull(pkg);
        Preconditions.checkNotNull(repositoryObjectId);

        Repository repository = Repository.get(objectContext, repositoryObjectId);

        // first, check to see if the package is there or not.

        Optional<org.haikuos.haikudepotserver.dataobjects.Pkg> persistedPkgOptional = org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(objectContext, pkg.getName());
        org.haikuos.haikudepotserver.dataobjects.Pkg persistedPkg;
        Optional<org.haikuos.haikudepotserver.dataobjects.PkgVersion> persistedLatestExistingPkgVersion = Optional.absent();
        Architecture architecture = Architecture.getByCode(objectContext, pkg.getArchitecture().name().toLowerCase()).get();
        org.haikuos.haikudepotserver.dataobjects.PkgVersion persistedPkgVersion = null;

        if(!persistedPkgOptional.isPresent()) {

            persistedPkg = objectContext.newObject(org.haikuos.haikudepotserver.dataobjects.Pkg.class);
            persistedPkg.setName(pkg.getName());
            persistedPkg.setActive(Boolean.TRUE);

            logger.info("the package {} did not exist; will create",pkg.getName());
        }
        else {

            persistedPkg = persistedPkgOptional.get();

            // if we know that the package exists then we should look for the version.

            SelectQuery selectQuery = new SelectQuery(
                    org.haikuos.haikudepotserver.dataobjects.PkgVersion.class,
                    ExpressionFactory.matchExp(
                            org.haikuos.haikudepotserver.dataobjects.PkgVersion.PKG_PROPERTY,
                            persistedPkg)
                            .andExp(toExpression(pkg.getVersion())));

            persistedPkgVersion = Iterables.getOnlyElement(
                    (List<org.haikuos.haikudepotserver.dataobjects.PkgVersion>) objectContext.performQuery(selectQuery),
                    null);

            persistedLatestExistingPkgVersion = PkgVersion.getLatestForPkg(
                    objectContext,
                    persistedPkg,
                    Collections.singletonList(architecture));
        }

        if(null==persistedPkgVersion) {

            persistedPkgVersion = objectContext.newObject(org.haikuos.haikudepotserver.dataobjects.PkgVersion.class);
            persistedPkgVersion.setActive(Boolean.TRUE);
            persistedPkgVersion.setMajor(pkg.getVersion().getMajor());
            persistedPkgVersion.setMinor(pkg.getVersion().getMinor());
            persistedPkgVersion.setMicro(pkg.getVersion().getMicro());
            persistedPkgVersion.setPreRelease(pkg.getVersion().getPreRelease());
            persistedPkgVersion.setRevision(pkg.getVersion().getRevision());
            persistedPkgVersion.setRepository(repository);
            persistedPkgVersion.setArchitecture(architecture);
            persistedPkgVersion.setPkg(persistedPkg);

            // now add the copyrights
            for(String copyright : pkg.getCopyrights()) {
                PkgVersionCopyright persistedPkgVersionCopyright = objectContext.newObject(PkgVersionCopyright.class);
                persistedPkgVersionCopyright.setBody(copyright);
                persistedPkgVersionCopyright.setPkgVersion(persistedPkgVersion);
            }

            // now add the licenses
            for(String license : pkg.getLicenses()) {
                PkgVersionLicense persistedPkgVersionLicense = objectContext.newObject(PkgVersionLicense.class);
                persistedPkgVersionLicense.setBody(license);
                persistedPkgVersionLicense.setPkgVersion(persistedPkgVersion);
            }

            if(null!=pkg.getHomePageUrl()) {
                PkgVersionUrl persistedPkgVersionUrl = objectContext.newObject(PkgVersionUrl.class);
                persistedPkgVersionUrl.setUrl(pkg.getHomePageUrl().getUrl());
                persistedPkgVersionUrl.setPkgUrlType(PkgUrlType.getByCode(
                        objectContext,
                        pkg.getHomePageUrl().getUrlType().name().toLowerCase()).get());
                persistedPkgVersionUrl.setPkgVersion(persistedPkgVersion);
            }

            if(!Strings.isNullOrEmpty(pkg.getSummary()) || !Strings.isNullOrEmpty(pkg.getDescription())) {
                updatePkgVersionLocalization(
                        objectContext,
                        persistedPkgVersion,
                        NaturalLanguage.getByCode(objectContext, NaturalLanguage.CODE_ENGLISH).get(),
                        pkg.getSummary(),
                        pkg.getDescription());
            }

            // look back at the previous version of the same package and see if there are localizations.  If there
            // are then replicate those into this version as well, but only if the english variant exists.

            if(persistedLatestExistingPkgVersion.isPresent()) {
                int naturalLanguagesReplicated = replicateLocalizationIfEnglishMatches(
                        objectContext,
                        persistedLatestExistingPkgVersion.get(),
                        persistedPkgVersion,
                        NaturalLanguage.getAllExceptEnglish(objectContext),
                        false).size();

                logger.info(
                        "replicated {} natural language localizations when creating new version of package {}",
                        naturalLanguagesReplicated,
                        pkg.getName());
            }

            logger.info(
                    "the version {} of package {} did not exist; will create",
                    pkg.getVersion().toString(),
                    pkg.getName());
        }

        logger.info("have processed package {}",pkg.toString());

    }

    // -------------------------------------
    // LOCALIZATION

    /**
     * <p>This method will either find the existing localization or create a new one.  It will then set the localized
     * values for the package.  Note that null or empty values will be treated the same and these values will be
     * trimmed.  Note that if the summary and description are null or empty string then if there is an existing
     * localization value, that this localization value will be deleted.</p>
     */

    public PkgVersionLocalization updatePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            NaturalLanguage naturalLanguage,
            String summary,
            String description) {

        Preconditions.checkNotNull(naturalLanguage);

        if(null!=summary) {
            summary = summary.trim();
        }

        if(null!=description) {
            description = description.trim();
        }

        boolean summaryNullOrEmpty = Strings.isNullOrEmpty(summary);
        boolean descriptionNullOrEmpty = Strings.isNullOrEmpty(description);

        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional =
                pkgVersion.getPkgVersionLocalization(naturalLanguage);

        if(summaryNullOrEmpty != descriptionNullOrEmpty) {
            throw new IllegalStateException("it is not possible to store a pkg version localization if either of the summary or description are missing");
        }

        if(summaryNullOrEmpty && descriptionNullOrEmpty) {

            if(pkgVersionLocalizationOptional.isPresent()) {
                pkgVersion.removeToManyTarget(
                        PkgVersion.PKG_VERSION_LOCALIZATIONS_PROPERTY,
                        pkgVersionLocalizationOptional.get(),
                        true);

                context.deleteObjects(pkgVersionLocalizationOptional.get());
            }

            return null;

        }
        else {

            PkgVersionLocalization pkgVersionLocalization;

            if (!pkgVersionLocalizationOptional.isPresent()) {
                pkgVersionLocalization = context.newObject(PkgVersionLocalization.class);
                pkgVersionLocalization.setNaturalLanguage(naturalLanguage);
                pkgVersion.addToManyTarget(PkgVersion.PKG_VERSION_LOCALIZATIONS_PROPERTY, pkgVersionLocalization, true);
            } else {
                pkgVersionLocalization = pkgVersionLocalizationOptional.get();
            }

            pkgVersionLocalization.setDescription(description);
            pkgVersionLocalization.setSummary(summary);

            return pkgVersionLocalization;
        }

    }

    /**
     * <p>This method will replicate the localization of specific natural languages if and only if the english
     * language variant is the same.  It will return the natural languages for which the operation was performed.</p>
     */

    public List<NaturalLanguage> replicateLocalizationIfEnglishMatches(
            ObjectContext context,
            PkgVersion pkgVersionSource,
            PkgVersion pkgVersionDestination,
            List<NaturalLanguage> naturalLanguages,
            boolean allowOverrideDestination) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkgVersionSource);
        Preconditions.checkNotNull(pkgVersionDestination);
        Preconditions.checkNotNull(naturalLanguages);

        // check that english is not in the destination languages.

        for(NaturalLanguage naturalLanguage : naturalLanguages) {
            if(naturalLanguage.getCode().equals(NaturalLanguage.CODE_ENGLISH)) {
                throw new IllegalStateException("it is not possible to replicate to the english language");
            }
        }

        Optional<PkgVersionLocalization> sourceEn = pkgVersionSource.getPkgVersionLocalization(NaturalLanguage.CODE_ENGLISH);
        Optional<PkgVersionLocalization> destinationEn = pkgVersionSource.getPkgVersionLocalization(NaturalLanguage.CODE_ENGLISH);

        if(
                sourceEn.isPresent()
                && destinationEn.isPresent()
                && sourceEn.get().equalsForContent(destinationEn.get()) ) {

            List<NaturalLanguage> naturalLanguagesEffected = Lists.newArrayList();

            for(NaturalLanguage naturalLanguage : naturalLanguages) {

                Optional<PkgVersionLocalization> sourceOther = pkgVersionSource.getPkgVersionLocalization(naturalLanguage.getCode());

                // if there is no record of the source language then there's nothing to copy so no point in copying.

                if(sourceOther.isPresent()) {

                    Optional<PkgVersionLocalization> destinationOther = pkgVersionDestination.getPkgVersionLocalization(naturalLanguage.getCode());

                    // if there is already a destination language then don't override it unless the client actually
                    // wants to explicitly override the destination.

                    if(!destinationOther.isPresent() || allowOverrideDestination) {

                        updatePkgVersionLocalization(
                                context,
                                pkgVersionDestination,
                                naturalLanguage,
                                sourceOther.get().getSummary(),
                                sourceOther.get().getDescription());

                        naturalLanguagesEffected.add(naturalLanguage);
                    }
                }
            }

            return naturalLanguagesEffected;
        }

        return Collections.emptyList();
    }

}
