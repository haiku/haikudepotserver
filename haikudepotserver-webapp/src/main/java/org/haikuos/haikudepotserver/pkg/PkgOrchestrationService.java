/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.access.OptimisticLockException;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.*;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.dataobjects.auto._Pkg;
import org.haikuos.haikudepotserver.pkg.model.*;
import org.haikuos.haikudepotserver.support.*;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>This service undertakes non-trivial operations on packages.</p>
 */

@Service
public class PkgOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgOrchestrationService.class);

    protected static int PAYLOAD_LENGTH_CONNECT_TIMEOUT = 10 * 1000;
    protected static int PAYLOAD_LENGTH_READ_TIMEOUT = 10 * 1000;

    protected static int SCREENSHOT_SIDE_LIMIT = 1500;

    // these seem like reasonable limits for the size of image data to have to
    // handle in-memory.

    protected static int SCREENSHOT_SIZE_LIMIT = 2 * 1024 * 1024; // 2MB
    protected static int ICON_SIZE_LIMIT = 100 * 1024; // 100k

    @Resource
    private PngOptimizationService pngOptimizationService;

    private ImageHelper imageHelper = new ImageHelper();

    // ------------------------------
    // HELP

    /**
     * <p>This method will read in the quantity of bytes from the input stream upto the limit.  If the limit is
     * reached, the method will throw {@link org.haikuos.haikudepotserver.pkg.model.SizeLimitReachedException}.</p>
     */

    private static byte[] toByteArray(InputStream inputStream, int sizeLimit) throws IOException {
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
    // QUERY

    /**
     * <p>This method will return the latest PkgVersion for the supplied package.  The version sorting logic to compare
     * version is quite complex.  For this reason it is basically implemented in java code.  An initial SQL statement
     * is executed to get meta-data for all of the possible versions.  This meta data then drives a sort and the sort
     * is able to provide the primary key for the latest version which is then faulted as a data object.</p>
     */

    public Optional<PkgVersion> getLatestPkgVersionForPkg(
            ObjectContext context,
            Pkg pkg,
            final List<Architecture> architectures) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkNotNull(architectures);
        Preconditions.checkState(!architectures.isEmpty());

        SelectQuery query = new SelectQuery(
                PkgVersion.class,
                ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkg)
                        .andExp(ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE))
                        .andExp(ExpressionFactory.matchExp(PkgVersion.IS_LATEST_PROPERTY, Boolean.TRUE))
                        .andExp(ExpressionFactory.inExp(PkgVersion.ARCHITECTURE_PROPERTY, architectures))
        );

        @SuppressWarnings("unchecked") List<PkgVersion> pkgVersions = (List<PkgVersion>) context.performQuery(query);

        switch(pkgVersions.size()) {

            case 0:
                return Optional.absent();

            case 1:
                return Optional.of(Iterables.getOnlyElement(pkgVersions));

            default:
                throw new IllegalStateException("more than one latest version found for pkg '"+pkg.getName()+"'");

        }
    }

    // ------------------------------
    // SEARCH

    public List<PkgVersion> search(
            ObjectContext context,
            PkgSearchSpecification search) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(search.getNaturalLanguage());
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        SQLTemplate sqlTemplate = (SQLTemplate) context.getEntityResolver().getQuery("SearchPkgVersions");
        Query query = sqlTemplate.createQuery(ImmutableMap.of(
                "search",search,
                "isTotal",false,
                "englishNaturalLanguage", NaturalLanguage.getEnglish(context)
        ));

        return (List<PkgVersion>) context.performQuery(query);
    }

    /**
     * <p>This method will provide a total of the package versions.</p>
     */

    public long total(
            ObjectContext context,
            PkgSearchSpecification search) {

        SQLTemplate sqlTemplate = (SQLTemplate) context.getEntityResolver().getQuery("SearchPkgVersions");
        SQLTemplate query = (SQLTemplate) sqlTemplate.createQuery(ImmutableMap.of(
                "search",search,
                "isTotal",true,
                "englishNaturalLanguage", NaturalLanguage.getEnglish(context)
        ));
        query.setFetchingDataRows(true);

        DataRow dataRow = (DataRow) (context.performQuery(query)).get(0);
        Number newTotal = (Number) dataRow.get("total");

        return newTotal.longValue();
    }

    // ------------------------------
    // EACH PACKAGE

    private void appendEjbqlAllPkgsWhere(
            Appendable ejbql,
            List<Object> parameterList,
            ObjectContext context,
            boolean allowSourceOnly) throws IOException {

        ejbql.append("p.active=true\n");

        if(!allowSourceOnly) {
            ejbql.append("AND EXISTS(");
            ejbql.append("SELECT pv FROM PkgVersion pv WHERE pv.pkg=p AND pv.active=true AND pv.architecture <> ?");
            parameterList.add(Architecture.getByCode(context,Architecture.CODE_SOURCE).get());
            ejbql.append(Integer.toString(parameterList.size()));
            ejbql.append(")");
        }

    }

    /**
     * <p>This method will provide a total of the packages.</p>
     */

    public long totalPkg(
            ObjectContext context,
            boolean allowSourceOnly) {
        Preconditions.checkArgument(null!=context, "the object context must be provided");

        StringBuilder ejbql = new StringBuilder();
        List<Object> parameterList = Lists.newArrayList();

        ejbql.append("SELECT COUNT(p) FROM Pkg p WHERE \n");

        try {
            appendEjbqlAllPkgsWhere(ejbql, parameterList, context, allowSourceOnly);
        }
        catch(IOException ioe) {
            throw new IllegalStateException("it was not possible to render the ejbql to get the packages", ioe);
        }

        EJBQLQuery query = new EJBQLQuery(ejbql.toString());

        for(int i=0;i<parameterList.size();i++) {
            query.setParameter(i+1, parameterList.get(i));
        }

        List<Object> result = (List<Object>) context.performQuery(query);

        if(1==result.size()) {
            return ((Number) result.get(0)).longValue();
        }

        throw new IllegalStateException("expecting one result with the total record count");
    }

    /**
     * <p>This will be called for each package in the system.</p>
     * @param c is the callback to invoke.
     * @param allowSourceOnly when true implies that a package can be processed which only has versions that are for
     *                        the source architecture.
     * @return the quantity of packages processed.
     */

    public long eachPkg(
            ObjectContext context,
            boolean allowSourceOnly,
            Callback<Pkg> c) {
        Preconditions.checkArgument(null!=c, "the callback should be provided to run for each package");
        Preconditions.checkArgument(null!=context, "the object context must be provided");

        int offset = 0;

        StringBuilder ejbql = new StringBuilder();
        List<Object> parameterList = Lists.newArrayList();

        ejbql.append("SELECT p FROM Pkg p WHERE \n");

        try {
            appendEjbqlAllPkgsWhere(ejbql, parameterList, context, allowSourceOnly);
        }
        catch(IOException ioe) {
            throw new IllegalStateException("it was not possible to render the ejbql to get the packages", ioe);
        }

        ejbql.append("\nORDER BY p.name ASC");

        EJBQLQuery query = new EJBQLQuery(ejbql.toString());

        for(int i=0;i<parameterList.size();i++) {
            query.setParameter(i+1, parameterList.get(i));
        }

        query.setFetchLimit(100);

        while(true) {

            query.setFetchOffset(offset);

            List<Pkg> pkgs = (List<Pkg>) context.performQuery(query);

            if(pkgs.isEmpty()) {
                return offset; // stop
            }
            else {
                for(Pkg pkg : pkgs) {

                    offset++;

                    if(!c.process(pkg)) {
                        return offset;
                    }
                }
            }
        }
    }

    // ------------------------------
    // ICONS

    /**
     * <p>This method will write the icon data supplied in the input to the package as its icon.  Note that the icon
     * must comply with necessary characteristics; for example it must be either 16 or 32 pixels along both its sides
     * if it is a PNG.  If it is non-compliant then an instance of
     * {@link org.haikuos.haikudepotserver.pkg.model.BadPkgIconException} will be thrown.</p>
     *
     * <p>This method will also use apply PNG optimization if this is possible.</p>
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

        Optional<PkgIcon> pkgIconOptional;
        Integer size = null;

        if(com.google.common.net.MediaType.PNG.toString().equals(mediaType.getCode())) {

            ImageHelper.Size pngSize =  imageHelper.derivePngSize(imageData);

            if(null==pngSize) {
                LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it is not a valid png image", pkg.getName());
                throw new BadPkgIconException();
            }

            if(!pngSize.areSides(16) && !pngSize.areSides(32)) {
                LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it must be either 32x32 or 16x16 px, but was {}", pkg.getName(), pngSize.toString());
                throw new BadPkgIconException();
            }

            if(null!=expectedSize && !pngSize.areSides(expectedSize)) {
                LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size did not match the expected size", pkg.getName());
                throw new BadPkgIconException();
            }

            if(pngOptimizationService.isConfigured()) {
                try {
                    imageData = pngOptimizationService.optimize(imageData);
                }
                catch(IOException ioe) {
                    throw new RuntimeException("the png optimization process has failed; ", ioe);
                }
            }
            else {
                LOGGER.info("skipping png optimization because the service is not configured");
            }

            size = pngSize.width;
            pkgIconOptional = pkg.getPkgIcon(mediaType, pngSize.width);
        }
        else {
            if(MediaType.MEDIATYPE_HAIKUVECTORICONFILE.equals(mediaType.getCode())) {
                if(!imageHelper.looksLikeHaikuVectorIconFormat(imageData)) {
                    LOGGER.warn("attempt to set the vector (hvif) package icon for package {}, but the data does not look like hvif", pkg.getName());
                    throw new BadPkgIconException();
                }
                pkgIconOptional = pkg.getPkgIcon(mediaType, null);
            }
            else {
                throw new IllegalStateException("unhandled media type; "+mediaType.getCode());
            }
        }

        PkgIconImage pkgIconImage;

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
            LOGGER.info("the icon {}px for package {} has been updated", size, pkg.getName());
        }
        else {
            LOGGER.info("the icon for package {} has been updated", pkg.getName());
        }

        return pkgIconOptional.get();
    }

    private List<MediaType> getInUsePkgIconMediaTypes(final ObjectContext context) {
        StringBuilder queryString = new StringBuilder();

        queryString.append("SELECT ");
        queryString.append(" DISTINCT pi.");
        queryString.append(PkgIcon.MEDIA_TYPE_PROPERTY + "." + MediaType.CODE_PROPERTY);
        queryString.append(" FROM ");
        queryString.append(PkgIcon.class.getSimpleName());
        queryString.append(" pi");

        EJBQLQuery query = new EJBQLQuery(queryString.toString());

        final List<String> codes = (List<String>) context.performQuery(query);

        return Lists.transform(
                codes,
                new Function<String, MediaType>() {
                    @Override
                    public MediaType apply(String input) {
                        return MediaType.getByCode(context, input).get();
                    }
                }
        );

    }

    private List<Integer> getInUsePkgIconSizes(ObjectContext context, MediaType mediaType) {
        StringBuilder queryString = new StringBuilder();

        queryString.append("SELECT ");
        queryString.append(" DISTINCT pi.");
        queryString.append(PkgIcon.SIZE_PROPERTY);
        queryString.append(" FROM ");
        queryString.append(PkgIcon.class.getSimpleName());
        queryString.append(" pi WHERE pi.");
        queryString.append(PkgIcon.MEDIA_TYPE_PROPERTY);
        queryString.append(" = :mediaType");

        EJBQLQuery query = new EJBQLQuery(queryString.toString());
        query.setParameter("mediaType", mediaType);

        return (List<Integer>) context.performQuery(query);
    }

    /**
     * <p>The packages are configured with icons.  Each icon has a media type and,
     * optionally a size.  This method will return all of those possible media
     * type + size combinations that are actually in use at the moment.  The list
     * will be unique.</p>
     */

    public List<PkgIconConfiguration> getInUsePkgIconConfigurations(ObjectContext objectContext) {
        Preconditions.checkState(null!=objectContext,"the object context must be supplied");
        List<PkgIconConfiguration> result = Lists.newArrayList();

        for(MediaType mediaType : getInUsePkgIconMediaTypes(objectContext)) {
            List<Integer> sizes = getInUsePkgIconSizes(objectContext, mediaType);

            if(sizes.isEmpty()) {
                result.add(new PkgIconConfiguration(mediaType, null));
            }
            else {
                for(Integer size : sizes) {
                    result.add(new PkgIconConfiguration(mediaType, size));
                }
            }
        }

        Collections.sort(result);

        return result;
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
            LOGGER.warn("attempt to store a screenshot image that is not a png");
            throw new BadPkgScreenshotException();
        }

        // check that the file roughly looks like PNG and the size is something
        // reasonable.

        if(size.height > SCREENSHOT_SIDE_LIMIT || size.width > SCREENSHOT_SIDE_LIMIT) {
            LOGGER.warn("attempt to store a screenshot image that is too large; " + size.toString());
            throw new BadPkgScreenshotException();
        }

        MediaType png = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get();

        // now we need to know the largest ordering so we can add this one at the end of the orderings
        // such that it is the next one in the list.

        int ordering = 1;
        Optional<Integer> highestExistingScreenshotOrdering = pkg.getHighestPkgScreenshotOrdering();

        if(highestExistingScreenshotOrdering.isPresent()) {
            ordering = highestExistingScreenshotOrdering.get() + 1;
        }

        PkgScreenshot screenshot = context.newObject(PkgScreenshot.class);
        screenshot.setCode(UUID.randomUUID().toString());
        screenshot.setOrdering(ordering);
        screenshot.setHeight(size.height);
        screenshot.setWidth(size.width);
        screenshot.setLength(pngData.length);
        pkg.addToManyTarget(Pkg.PKG_SCREENSHOTS_PROPERTY, screenshot, true);

        PkgScreenshotImage screenshotImage = context.newObject(PkgScreenshotImage.class);
        screenshotImage.setMediaType(png);
        screenshotImage.setData(pngData);
        screenshot.addToManyTarget(PkgScreenshot.PKG_SCREENSHOT_IMAGES_PROPERTY, screenshotImage, true);

        pkg.setModifyTimestamp(new java.util.Date());

        LOGGER.info("a screenshot #{} has been added to package {} ({})", ordering, pkg.getName(), screenshot.getCode());

        return screenshot;
    }

    // ------------------------------
    // IMPORT

    /**
     * <p>Downloads the nominated package's payload in order to ascertain how long it is.</p>
     */

    public long payloadLength(PkgVersion pkgVersion) throws IOException {
        Preconditions.checkArgument(null!=pkgVersion);

        long result = -1;
        URL pkgVersionHpkgURL = pkgVersion.getHpkgURL();
        HttpURLConnection connection = null;

        LOGGER.info(
                "will obtain length for pkg version {} from; {}",
                pkgVersion.toStringWithPkgAndArchitecture(),
                pkgVersionHpkgURL.toString());

        switch(pkgVersionHpkgURL.getProtocol()) {

            case "http":
            case "https":

                try {
                    connection = (HttpURLConnection) pkgVersionHpkgURL.openConnection();

                    connection.setConnectTimeout(PAYLOAD_LENGTH_CONNECT_TIMEOUT);
                    connection.setReadTimeout(PAYLOAD_LENGTH_READ_TIMEOUT);
                    connection.setRequestMethod("HEAD");
                    connection.connect();

                    String contentLengthHeader = connection.getHeaderField(HttpHeaders.CONTENT_LENGTH);

                    if(!Strings.isNullOrEmpty(contentLengthHeader)) {
                        long contentLength;

                        try {
                            contentLength = Long.parseLong(contentLengthHeader);

                            if(contentLength > 0) {
                                result = contentLength;
                            }
                            else {
                                LOGGER.warn("bad content length; {}", contentLength);
                            }
                        }
                        catch(NumberFormatException nfe) {
                            LOGGER.warn("malformed content length; {}", contentLengthHeader);
                        }
                    }
                    else {
                        LOGGER.warn("unable to get the content length header");
                    }

                } finally {
                    if (null != connection) {
                        connection.disconnect();
                    }
                }
                break;

            case "file":
                File file = new File(pkgVersionHpkgURL.getPath());

                if(file.exists() && file.isFile()) {
                    result = file.length();
                }
                else {
                    LOGGER.warn("unable to find the package file; {}", pkgVersionHpkgURL.getPath());
                }
                break;

        }

        LOGGER.info(
                "did obtain length for pkg version {}; {}",
                pkgVersion.toStringWithPkgAndArchitecture(),
                result);

        return result;
    }

    /**
     * <p>This method will deactivate package versions for a package where the package version is related to the
     * supplied repository.  This is used in the situation where a package was once part of a repository, but has
     * been removed.</p>
     * @return the quantity of package versions that were deactivated.
     */

    public int deactivatePkgVersionsForPkgAssociatedWithRepository(
            ObjectContext context,
            Pkg pkg,
            final Repository repository) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkNotNull(repository);

        int count = 0;

        for(PkgVersion pkgVersion : PkgVersion.getForPkg(context, pkg)) {
            if(pkgVersion.getRepository().equals(repository)) {
                if(pkgVersion.getActive()) {
                    pkgVersion.setActive(false);
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * <p>This method will return all of the package names that have package versions that are related to a
     * repository.</p>
     */

    public Set<String> fetchPkgNamesWithAnyPkgVersionAssociatedWithRepository(
            ObjectContext context,
            Repository repository) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(repository);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT p.name FROM ");
        queryBuilder.append(Pkg.class.getSimpleName());
        queryBuilder.append(" p WHERE EXISTS(SELECT pv FROM ");
        queryBuilder.append(PkgVersion.class.getSimpleName());
        queryBuilder.append(" pv WHERE pv.");
        queryBuilder.append(PkgVersion.PKG_PROPERTY);
        queryBuilder.append("=p AND pv.");
        queryBuilder.append(PkgVersion.REPOSITORY_PROPERTY);
        queryBuilder.append("=:repository)");

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());
        query.setParameter("repository",repository);

        return ImmutableSet.copyOf(context.performQuery(query));
    }

    private VersionCoordinates toVersionCoordinates(org.haikuos.pkg.model.PkgVersion version) {
        return new VersionCoordinates(
                version.getMajor(),
                version.getMinor(),
                version.getMicro(),
                version.getPreRelease(),
                version.getRevision());
    }

    /**
     * <p>This method will import the package described by the 'pkg' parameter by locating the package and
     * either creating it or updating it as necessary.</p>
     * @param pkg imports into the local database from this package model.
     * @param populatePayloadLength is able to signal to the import process that the length of the package should be
     *                              populated.
     */

    public void importFrom(
            ObjectContext objectContext,
            ObjectId repositoryObjectId,
            org.haikuos.pkg.model.Pkg pkg,
            boolean populatePayloadLength) {

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
            persistedPkg.setProminence(Prominence.getByOrdering(objectContext, Prominence.ORDERING_LAST).get());

            LOGGER.info("the package {} did not exist; will create", pkg.getName());
        }
        else {

            persistedPkg = persistedPkgOptional.get();

            // if we know that the package exists then we should look for the version.

            List<Expression> expressions = ImmutableList.of(
                    ExpressionFactory.matchExp(
                            org.haikuos.haikudepotserver.dataobjects.PkgVersion.PKG_PROPERTY,
                            persistedPkg
                    ),
                    ExpressionHelper.toExpression(toVersionCoordinates(pkg.getVersion())),
                    ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, architecture)
            );


            SelectQuery selectQuery = new SelectQuery(
                    org.haikuos.haikudepotserver.dataobjects.PkgVersion.class,
                    ExpressionHelper.andAll(expressions)
            );

            //noinspection unchecked
            persistedPkgVersion = Iterables.getOnlyElement(
                    (List<org.haikuos.haikudepotserver.dataobjects.PkgVersion>) objectContext.performQuery(selectQuery),
                    null);

            persistedLatestExistingPkgVersion = getLatestPkgVersionForPkg(
                    objectContext,
                    persistedPkg,
                    Collections.singletonList(architecture));
        }

        if(null==persistedPkgVersion) {

            persistedPkgVersion = objectContext.newObject(org.haikuos.haikudepotserver.dataobjects.PkgVersion.class);
            persistedPkgVersion.setMajor(pkg.getVersion().getMajor());
            persistedPkgVersion.setMinor(pkg.getVersion().getMinor());
            persistedPkgVersion.setMicro(pkg.getVersion().getMicro());
            persistedPkgVersion.setPreRelease(pkg.getVersion().getPreRelease());
            persistedPkgVersion.setRevision(pkg.getVersion().getRevision());
            persistedPkgVersion.setRepository(repository);
            persistedPkgVersion.setArchitecture(architecture);
            persistedPkgVersion.setPkg(persistedPkg);

            LOGGER.info(
                    "the version {} of package {} did not exist; will create",
                    pkg.getVersion().toString(),
                    pkg.getName());
        }
        else {

            LOGGER.debug(
                    "the version {} of package {} did exist; will re-configure necessary data",
                    pkg.getVersion().toString(),
                    pkg.getName());

        }

        persistedPkgVersion.setActive(Boolean.TRUE);

        {
            List<String> existingCopyrights = persistedPkgVersion.getCopyrights();

            // now add the copyrights that are not already there.

            for (String copyright : pkg.getCopyrights()) {
                if(!existingCopyrights.contains(copyright)) {
                    PkgVersionCopyright persistedPkgVersionCopyright = objectContext.newObject(PkgVersionCopyright.class);
                    persistedPkgVersionCopyright.setBody(copyright);
                    persistedPkgVersionCopyright.setPkgVersion(persistedPkgVersion);
                }
            }

            // remove those copyrights that are no longer present

            for(PkgVersionCopyright pkgVersionCopyright : ImmutableList.copyOf(persistedPkgVersion.getPkgVersionCopyrights())) {
                if(!pkg.getCopyrights().contains(pkgVersionCopyright.getBody())) {
                    persistedPkgVersion.removeFromPkgVersionCopyrights(pkgVersionCopyright);
                    objectContext.deleteObjects(pkgVersionCopyright);
                }
            }

        }

        {
            List<String> existingLicenses = persistedPkgVersion.getLicenses();

            // now add the licenses that are not already there.

            for (String license : pkg.getLicenses()) {
                if(!existingLicenses.contains(license)) {
                    PkgVersionLicense persistedPkgVersionLicense = objectContext.newObject(PkgVersionLicense.class);
                    persistedPkgVersionLicense.setBody(license);
                    persistedPkgVersionLicense.setPkgVersion(persistedPkgVersion);
                }
            }

            // remove those licenses that are no longer present

            for(PkgVersionLicense pkgVersionLicense : ImmutableList.copyOf(persistedPkgVersion.getPkgVersionLicenses())) {
                if(!pkg.getLicenses().contains(pkgVersionLicense.getBody())) {
                    persistedPkgVersion.removeFromPkgVersionLicenses(pkgVersionLicense);
                    objectContext.deleteObjects(pkgVersionLicense);
                }
            }
        }

        {
            PkgUrlType pkgUrlType = PkgUrlType.getByCode(
                    objectContext,
                    org.haikuos.pkg.model.PkgUrlType.HOMEPAGE.name().toLowerCase()).get();

            Optional<PkgVersionUrl> homeUrlOptional = persistedPkgVersion.getPkgVersionUrlForType(pkgUrlType);

            if (null != pkg.getHomePageUrl()) {
                if(homeUrlOptional.isPresent()) {
                    homeUrlOptional.get().setUrl(pkg.getHomePageUrl().getUrl());
                }
                else {
                    PkgVersionUrl persistedPkgVersionUrl = objectContext.newObject(PkgVersionUrl.class);
                    persistedPkgVersionUrl.setUrl(pkg.getHomePageUrl().getUrl());
                    persistedPkgVersionUrl.setPkgUrlType(pkgUrlType);
                    persistedPkgVersionUrl.setPkgVersion(persistedPkgVersion);
                }
            }
        }

        if(!Strings.isNullOrEmpty(pkg.getSummary()) || !Strings.isNullOrEmpty(pkg.getDescription())) {
            updatePkgVersionLocalization(
                    objectContext,
                    persistedPkgVersion,
                    NaturalLanguage.getByCode(objectContext, NaturalLanguage.CODE_ENGLISH).get(),
                    null, // not supported quite yet
                    pkg.getSummary(),
                    pkg.getDescription());
        }

        // now possibly switch the latest flag over to the new one from the old one.

        if(persistedLatestExistingPkgVersion.isPresent()) {
            VersionCoordinatesComparator versionCoordinatesComparator = new VersionCoordinatesComparator();
            VersionCoordinates persistedPkgVersionCoords = persistedPkgVersion.toVersionCoordinates();
            VersionCoordinates persistedLatestExistingPkgVersionCoords = persistedLatestExistingPkgVersion.get().toVersionCoordinates();

            int c = versionCoordinatesComparator.compare(
                    persistedPkgVersionCoords,
                    persistedLatestExistingPkgVersionCoords);

            if(c > 0) {
                persistedPkgVersion.setIsLatest(true);
                persistedLatestExistingPkgVersion.get().setIsLatest(false);
            }
            else {
                boolean isRealArchitecture = !persistedPkgVersion.getArchitecture().getCode().equals(Architecture.CODE_SOURCE);

                if(0==c) {
                    if(isRealArchitecture) {
                        LOGGER.debug(
                                "imported a package version {} of {} which is the same as the existing {}",
                                persistedPkgVersionCoords,
                                persistedPkgVersion.getPkg().getName(),
                                persistedLatestExistingPkgVersionCoords);
                    }
                }
                else {
                    if(isRealArchitecture) {
                        LOGGER.warn(
                                "imported a package version {} of {} which is older or the same as the existing {}",
                                persistedPkgVersionCoords,
                                persistedPkgVersion.getPkg().getName(),
                                persistedLatestExistingPkgVersionCoords);
                    }
                }
            }
        }
        else {
            persistedPkgVersion.setIsLatest(true);
        }

        // [apl]
        // If this fails, we will let it go and it can be tried again a bit later on.  The system can try to back-fill
        // those at some later date if any of the latest versions for packages are missing.  This is better than
        // failing the import at this stage since this is "just" meta data.

        if(populatePayloadLength && null==persistedPkgVersion.getPayloadLength()) {
            long length = -1;

            try {
                length = payloadLength(persistedPkgVersion);
            }
            catch(IOException ioe) {
                LOGGER.error("unable to get the payload length for; " + persistedPkgVersion, ioe);
            }

            if(length > 0) {
                persistedPkgVersion.setPayloadLength(length);
            }
        }

        LOGGER.debug("have processed package {}", pkg.toString());

    }

    // -------------------------------------
    // LOCALIZATION

    private void fill(ResolvedPkgVersionLocalization result, PkgVersionLocalization pvl) {
        if(null==result.getTitle()) {
            result.setTitle(pvl.getTitle().orNull());
        }

        if(null==result.getSummary()) {
            result.setSummary(pvl.getSummary().orNull());
        }

        if(null==result.getDescription()) {
            result.setDescription(pvl.getDescription().orNull());
        }
    }

    private void fill(ResolvedPkgVersionLocalization result, PkgLocalization pl) {
        if(null==result.getTitle()) {
            result.setTitle(pl.getTitle());
        }

        if(null==result.getSummary()) {
            result.setSummary(pl.getSummary());
        }

        if(null==result.getDescription()) {
            result.setDescription(pl.getDescription());
        }
    }

    /**
     * <p>For a given package version, this method will look at the various levels of localization and fallback
     * options to English and will produce an object that represents the best language options.</p>
     */

    public ResolvedPkgVersionLocalization resolvePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            NaturalLanguage naturalLanguage) {

        Preconditions.checkArgument(null!=context);
        Preconditions.checkArgument(null!=pkgVersion);
        ResolvedPkgVersionLocalization result = new ResolvedPkgVersionLocalization();

        {
            Optional<PkgVersionLocalization> pvlNl = PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, naturalLanguage.getCode());

            if (pvlNl.isPresent()) {
                fill(result, pvlNl.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgLocalization> plNl = PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    naturalLanguage.getCode());

            if(plNl.isPresent()) {
                fill(result, plNl.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgVersionLocalization> pvlEn = PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, NaturalLanguage.CODE_ENGLISH);

            if(pvlEn.isPresent()) {
                fill(result, pvlEn.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgLocalization> plNl = PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    NaturalLanguage.CODE_ENGLISH);

            if(plNl.isPresent()) {
                fill(result, plNl.get());
            }
        }

        return result;
    }

    /**
     * <p>This method will update the localization defined in the parameters to this method into the data
     * structure for the package.</p>
     */

    public PkgLocalization updatePkgLocalization(
            ObjectContext context,
            Pkg pkg,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null!=pkg);
        Preconditions.checkNotNull(naturalLanguage);

        if(null!=title) {
            title = title.trim();
        }

        if(null!=summary) {
            summary = summary.trim();
        }

        if(null!=description) {
            description = description.trim();
        }

        Optional<PkgLocalization> pkgLocalizationOptional = PkgLocalization.getForPkgAndNaturalLanguageCode(context, pkg, naturalLanguage.getCode());

        if(Strings.isNullOrEmpty(title) && Strings.isNullOrEmpty(summary) && Strings.isNullOrEmpty(description)) {
            if(pkgLocalizationOptional.isPresent()) {
                context.deleteObject(pkgLocalizationOptional.get());
            }

            return null;
        }

        if(!pkgLocalizationOptional.isPresent()) {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(naturalLanguage);
            pkgLocalization.setTitle(title);
            pkgLocalization.setSummary(summary);
            pkgLocalization.setDescription(description);
            pkg.addToManyTarget(_Pkg.PKG_LOCALIZATIONS_PROPERTY, pkgLocalization, true);
            return pkgLocalization;
        }

        pkgLocalizationOptional.get().setTitle(title);
        pkgLocalizationOptional.get().setSummary(summary);
        pkgLocalizationOptional.get().setDescription(description);

        return pkgLocalizationOptional.get();
    }

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
            String title,
            String summary,
            String description) {

        Preconditions.checkNotNull(naturalLanguage);

        if(null!=title) {
            title = title.trim();
        }

        if(null!=summary) {
            summary = summary.trim();
        }

        if(null!=description) {
            description = description.trim();
        }

        boolean titleNullOrEmpty = Strings.isNullOrEmpty(title);
        boolean summaryNullOrEmpty = Strings.isNullOrEmpty(summary);
        boolean descriptionNullOrEmpty = Strings.isNullOrEmpty(description);

        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional =
                pkgVersion.getPkgVersionLocalization(naturalLanguage);

        if(titleNullOrEmpty && summaryNullOrEmpty && descriptionNullOrEmpty) {

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

            if(!descriptionNullOrEmpty) {
                pkgVersionLocalization.setDescriptionLocalizationContent(
                        LocalizationContent.getOrCreateLocalizationContent(context, description));
            }

            if(!summaryNullOrEmpty) {
                pkgVersionLocalization.setSummaryLocalizationContent(
                        LocalizationContent.getOrCreateLocalizationContent(context, summary));
            }

            if(!titleNullOrEmpty) {
                pkgVersionLocalization.setTitleLocalizationContent(
                        LocalizationContent.getOrCreateLocalizationContent(context, title));
            }

            return pkgVersionLocalization;
        }

    }

    // ------------------------------
    // MISC

    /**
     * <p>This method will update the {@link org.haikuos.haikudepotserver.dataobjects.PkgCategory} set in the
     * nominated {@link org.haikuos.haikudepotserver.dataobjects.Pkg} such that the supplied set are the
     * categories for the package.  It will do this by adding and removing relationships between the package
     * and the categories.</p>
     * @return true if a change was made.
     */

    public boolean updatePkgCategories(ObjectContext context, Pkg pkg, List<PkgCategory> pkgCategories) {
        Preconditions.checkArgument(null!=context);
        Preconditions.checkArgument(null!=pkg);
        Preconditions.checkArgument(null!=pkgCategories);

        pkgCategories = Lists.newArrayList(pkgCategories);
        boolean didChange = false;

        // now go through and delete any of those pkg relationships to packages that are already present
        // and which are no longer required.  Also remove those that we already have from the list.

        for(PkgPkgCategory pkgPkgCategory : ImmutableList.copyOf(pkg.getPkgPkgCategories())) {
            if(!pkgCategories.contains(pkgPkgCategory.getPkgCategory())) {
                pkg.removeToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
                context.deleteObjects(pkgPkgCategory);
                didChange = true;
            }
            else {
                pkgCategories.remove(pkgPkgCategory.getPkgCategory());
            }
        }

        // now any remaining in the pkgCategories will need to be added to the pkg.

        for(PkgCategory pkgCategory : pkgCategories) {
            PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
            pkgPkgCategory.setPkgCategory(pkgCategory);
            pkg.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
            didChange = true;
        }

        // now save and finish.

        if(didChange) {
            pkg.setModifyTimestamp();
        }

        return didChange;
    }

    /**
     * <p>This method will increment the view counter on a package version.  If it encounters an optimistic
     * locking problem then it will pause and it will try again in a moment.  It will attempt this a few
     * times and then fail with a runtime exception.</p>
     */

    public void incrementViewCounter(ServerRuntime serverRuntime, ObjectId pkgVersionOid) {

        int attempts = 3;

        while(true) {
            ObjectContext contextEdit = serverRuntime.getContext();
            PkgVersion pkgVersionEdit = (PkgVersion) Iterables.getOnlyElement(contextEdit.performQuery(new ObjectIdQuery(pkgVersionOid)));
            pkgVersionEdit.incrementViewCounter();

            try {
                contextEdit.commitChanges();
                LOGGER.info("did increment the view counter for '{}'", pkgVersionEdit.getPkg().toString());
                return;
            } catch (OptimisticLockException ole) {
                contextEdit.invalidateObjects(pkgVersionEdit);

                attempts--;

                if (0 == attempts) {
                    throw new RuntimeException("unable to increment the view counter for '"+pkgVersionEdit.getPkg().toString()+"' because of an optimistic locking failure; have exhausted attempts", ole);
                } else {
                    LOGGER.error("unable to increment the view counter for '{}' because of an optimistic locking failure; will try again...", pkgVersionEdit.getPkg().toString());
                    Uninterruptibles.sleepUninterruptibly(250 + (System.currentTimeMillis() % 250), TimeUnit.MILLISECONDS);
                }
            }
        }
    }

}
