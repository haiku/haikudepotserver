/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._Pkg;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.support.*;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgUrlType;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>This service undertakes non-trivial operations on packages.</p>
 */

@Service
public class PkgOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgOrchestrationService.class);

    private static int PAYLOAD_LENGTH_CONNECT_TIMEOUT = 10 * 1000;
    private static int PAYLOAD_LENGTH_READ_TIMEOUT = 10 * 1000;

    private static int SCREENSHOT_SIDE_LIMIT = 1500;

    // TODO; should be injected as a pattern because this should not know about paths for the controller.
    public final static String URL_SEGMENT_PKGDOWNLOAD = "__pkgdownload";

    // these seem like reasonable limits for the size of image data to have to
    // handle in-memory.

    private static int SCREENSHOT_SIZE_LIMIT = 2 * 1024 * 1024; // 2MB
    private static int ICON_SIZE_LIMIT = 100 * 1024; // 100k

    /**
     * <p>This appears at the end of the package name to signify that it is a development package
     * for another package.</p>
     */

    static String SUFFIX_PKG_DEVELOPMENT = "_devel";

    static String SUFFIX_SUMMARY_DEVELOPMENT = " (development files)";

    @Resource
    private RenderedPkgIconRepository renderedPkgIconRepository;

    @Resource
    private PngOptimizationService pngOptimizationService;

    @Value("${architecture.default.code}")
    private String defaultArchitectureCode;

    private ImageHelper imageHelper = new ImageHelper();

    // ------------------------------
    // HELP

    /**
     * <p>This method will read in the quantity of bytes from the input stream upto the limit.  If the limit is
     * reached, the method will throw {@link SizeLimitReachedException}.</p>
     */

    private static byte[] toByteArray(InputStream inputStream, int sizeLimit) throws IOException {
        Preconditions.checkArgument(null != inputStream, "an input stream must be provided");
        Preconditions.checkArgument(sizeLimit > 0, "a size limit must be provided which is greater than zero");

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
     * <p>This method will return the latest version for a package in any architecture.</p>
     */

    public Optional<PkgVersion> getLatestPkgVersionForPkg(
            ObjectContext context,
            Pkg pkg,
            Repository repository) {

        Preconditions.checkArgument(null != context, "a context must be provided");
        Preconditions.checkArgument(null != pkg, "a package must be provided");

        Optional<PkgVersion> pkgVersionOptional = getLatestPkgVersionForPkg(
                context,
                pkg,
                repository,
                Collections.singletonList(Architecture.getByCode(context, defaultArchitectureCode).get()));

        if(!pkgVersionOptional.isPresent()) {
            List<Architecture> architectures = Architecture.getAllExceptByCode(
                    context,
                    ImmutableList.of(Architecture.CODE_SOURCE, defaultArchitectureCode));

            for (int i = 0; i < architectures.size() && !pkgVersionOptional.isPresent(); i++) {
                pkgVersionOptional = getLatestPkgVersionForPkg(
                        context,
                        pkg,
                        repository,
                        Collections.singletonList(architectures.get(i)));
            }
        }

        return pkgVersionOptional;
    }

    /**
     * <p>This method will return the latest PkgVersion for the supplied package.</p>
     */

    public Optional<PkgVersion> getLatestPkgVersionForPkg(
            ObjectContext context,
            Pkg pkg,
            Repository repository,
            final List<Architecture> architectures) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must must be provided");
        Preconditions.checkArgument(null != architectures && !architectures.isEmpty(), "the architectures must be provided and must not be empty");
        Preconditions.checkArgument(null != repository, "the repository must be provided");

        List<Expression> expressions = new ArrayList<>();
        expressions.add(ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkg));
        expressions.add(ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE));
        expressions.add(ExpressionFactory.matchExp(PkgVersion.IS_LATEST_PROPERTY, Boolean.TRUE));
        expressions.add(ExpressionFactory.inExp(PkgVersion.ARCHITECTURE_PROPERTY, architectures));
        expressions.add(ExpressionFactory.matchExp(
                PkgVersion.REPOSITORY_SOURCE_PROPERTY + "." + RepositorySource.REPOSITORY_PROPERTY,
                repository));

        SelectQuery query = new SelectQuery(PkgVersion.class, ExpressionHelper.andAll(expressions));
        return ((List<PkgVersion>) context.performQuery(query)).stream().collect(SingleCollector.optional());
    }

    /**
     * <p>For the given architecture and package, re-establish what is the latest package and correct it.
     * This may be necessary after, for example, adjusting the active flag on a pkg version.</p>
     * @return the updated latest package version or an empty option if there is none.
     */

    public Optional<PkgVersion> adjustLatest(
            ObjectContext context,
            Pkg pkg,
            Architecture architecture) {

        Preconditions.checkArgument(null != context, "a context is required");
        Preconditions.checkArgument(null != pkg, "the package must be supplied");
        Preconditions.checkArgument(null != architecture, "the architecture must be supplied");

        List<PkgVersion> pkgVersions = (List<PkgVersion>) context.performQuery(new SelectQuery(
                PkgVersion.class,
                ExpressionHelper.andAll(ImmutableList.of(
                        ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkg),
                        ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, architecture)
                ))
        ));

        if(!pkgVersions.isEmpty()) {

            final VersionCoordinatesComparator comparator = new VersionCoordinatesComparator();

            Optional<PkgVersion> pkgVersionOptional = pkgVersions
                    .stream()
                    .filter(PkgVersion::getActive)
                    .sorted((pv1, pv2) -> comparator.compare(pv2.toVersionCoordinates(), pv1.toVersionCoordinates()))
                    .findFirst();

            if(pkgVersionOptional.isPresent()) {
                pkgVersionOptional.get().setIsLatest(true);
            }

            for (PkgVersion pkgVersion : pkgVersions) {
                if (pkgVersion.getIsLatest() &&
                        (!pkgVersionOptional.isPresent() ||
                                !pkgVersion.equals(pkgVersionOptional.get())
                        )
                        ) {
                    pkgVersion.setIsLatest(false);
                }
            }

            return pkgVersionOptional;
        }

        return Optional.empty();
    }

    /**
     * <p>Given a {@link PkgVersion}, see if there is a corresponding source package.</p>
     */

    public Optional<PkgVersion> getCorrespondingSourcePkgVersion(
            ObjectContext context,
            PkgVersion pkgVersion) {

        Preconditions.checkArgument(null != context, "a context is required");
        Preconditions.checkArgument(null != pkgVersion, "a pkg version is required");

        Optional<Pkg> pkgSourceOptional = Pkg.getByName(context, pkgVersion.getPkg().getName() + "_source");

        if(pkgSourceOptional.isPresent()) {

            Architecture sourceArchitecture = Architecture.getByCode(
                    context,
                    Architecture.CODE_SOURCE).get();

            SelectQuery query = new SelectQuery(
                    PkgVersion.class,
                    ExpressionHelper.andAll(ImmutableList.of(
                            ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkgSourceOptional.get()),
                            ExpressionFactory.matchExp(
                                    PkgVersion.REPOSITORY_SOURCE_PROPERTY + "." + RepositorySource.REPOSITORY_PROPERTY,
                                    pkgVersion.getRepositorySource().getRepository()),
                            ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE),
                            ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, sourceArchitecture),
                            ExpressionHelper.toExpression(pkgVersion.toVersionCoordinates(), null)
                    ))
            );

            return ((List<PkgVersion>) context.performQuery(query)).stream().collect(SingleCollector.optional());
        }

        return Optional.empty();
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
                "search", search,
                "isTotal", false,
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
                "search", search,
                "isTotal", true,
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
        List<Object> parameterList = new ArrayList<>();

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
            StoppableConsumer<Pkg> c) {
        Preconditions.checkArgument(null!=c, "the callback should be provided to run for each package");
        Preconditions.checkArgument(null!=context, "the object context must be provided");

        int offset = 0;

        StringBuilder ejbql = new StringBuilder();
        List<Object> parameterList = new ArrayList<>();

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

                    if(!c.accept(pkg)) {
                        return offset;
                    }
                }
            }
        }
    }

    // ------------------------------
    // CHANGE LOG

    /**
     * <p>Performs necessary modifications to the package so that the changelog is updated
     * with the new content supplied.</p>
     */

    public void updatePkgChangelog(
            ObjectContext context,
            Pkg pkg,
            String newContent) {

        Preconditions.checkArgument(null!=context, "the context is not supplied");
        Preconditions.checkArgument(null!=pkg, "the pkg is not supplied");

        Optional<PkgChangelog> pkgChangelogOptional = pkg.getPkgChangelog();

        if(null!=newContent) {
            newContent = newContent.trim().replace("\r\n", "\n"); // windows to unix newline.
        }

        if(pkgChangelogOptional.isPresent()) {
            if(null==newContent) {
                context.deleteObject(pkgChangelogOptional.get());
                LOGGER.info("did remove the changelog for; {}", pkg);
            }
            else {
                pkgChangelogOptional.get().setContent(newContent);
                LOGGER.info("did update the changelog for; {}",pkg);
            }
        }
        else {
            if(null!=newContent) {
                PkgChangelog pkgChangelog = context.newObject(PkgChangelog.class);
                pkgChangelog.setPkg(pkg);
                pkgChangelog.setContent(newContent);
                LOGGER.info("did add a new changelog for; {}", pkg);
            }
        }
    }

    // ------------------------------
    // ICONS

    /**
     * <p>This method will write the icon data supplied in the input to the package as its icon.  Note that the icon
     * must comply with necessary characteristics; for example it must be either 16 or 32 pixels along both its sides
     * if it is a PNG.  If it is non-compliant then an instance of
     * {@link BadPkgIconException} will be thrown.</p>
     *
     * <p>This method will also use apply PNG optimization if this is possible.</p>
     */

    public PkgIcon storePkgIconImage(
            InputStream input,
            MediaType mediaType,
            Integer expectedSize,
            ObjectContext context,
            Pkg pkg) throws IOException, BadPkgIconException {

        Preconditions.checkArgument(null != context, "the context is not supplied");
        Preconditions.checkArgument(null != input, "the input must be provided");
        Preconditions.checkArgument(null != mediaType, "the mediaType must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");

        byte[] imageData = toByteArray(input, ICON_SIZE_LIMIT);

        Optional<PkgIcon> pkgIconOptional;
        Integer size = null;

        if(com.google.common.net.MediaType.PNG.toString().equals(mediaType.getCode())) {

            ImageHelper.Size pngSize =  imageHelper.derivePngSize(imageData);

            if(null==pngSize) {
                LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it is not a valid png image", pkg.getName());
                throw new BadPkgIconException();
            }

            if(!pngSize.areSides(16) && !pngSize.areSides(32) && !pngSize.areSides(64)) {
                LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it must be either 32x32 or 16x16 px, but was {}", pkg.getName(), pngSize.toString());
                throw new BadPkgIconException();
            }

            if(null!=expectedSize && !pngSize.areSides(expectedSize)) {
                LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size did not match the expected size", pkg.getName());
                throw new BadPkgIconException();
            }

            try {
                imageData = pngOptimizationService.optimize(imageData);
            }
            catch(IOException ioe) {
                throw new RuntimeException("the png optimization process has failed; ", ioe);
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
        renderedPkgIconRepository.evict(context, pkg);

        if(null!=size) {
            LOGGER.info("the icon {}px for package {} has been updated", size, pkg.getName());
        }
        else {
            LOGGER.info("the icon for package {} has been updated", pkg.getName());
        }

        propagateDataFromPkgToDevelPkg(context, pkg);

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

        return codes
                .stream()
                .map(c -> MediaType.getByCode(context, c).get())
                .collect(Collectors.toList());

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

        Preconditions.checkArgument(null!=objectContext,"the object context must be supplied");

        List<PkgIconConfiguration> result = new ArrayList<>();

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

        Preconditions.checkArgument(null != output, "the output stream must be provided");
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != screenshot, "the screenshot must be provided");
        Preconditions.checkArgument(targetHeight > 0, "the target height is <= 0");
        Preconditions.checkArgument(targetWidth > 0, "the target width is <= 0");

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
     * {@link BadPkgScreenshotException} will be thrown.</p>
     */

    public PkgScreenshot storePkgScreenshotImage(
            InputStream input,
            ObjectContext context,
            Pkg pkg) throws IOException, BadPkgScreenshotException {

        Preconditions.checkArgument(null != input, "the input must be provided");
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the package must be provided");

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
        Preconditions.checkArgument(null != pkgVersion, "the package version must be provided");

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
                    connection.setRequestMethod(HttpMethod.HEAD.name());
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

    public int deactivatePkgVersionsForPkgAssociatedWithRepositorySource(
            ObjectContext context,
            Pkg pkg,
            final RepositorySource repositorySource) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");
        Preconditions.checkArgument(null != repositorySource, "the repository source must be provided");

        int count = 0;

        for(PkgVersion pkgVersion : PkgVersion.getForPkg(context, pkg, repositorySource, false)) { // active only
            if(pkgVersion.getRepositorySource().equals(repositorySource)) {
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

    public Set<String> fetchPkgNamesWithAnyPkgVersionAssociatedWithRepositorySource(
            ObjectContext context,
            RepositorySource repositorySource) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != repositorySource, "the repository soures must be provided");

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT p.name FROM ");
        queryBuilder.append(Pkg.class.getSimpleName());
        queryBuilder.append(" p WHERE EXISTS(SELECT pv FROM ");
        queryBuilder.append(PkgVersion.class.getSimpleName());
        queryBuilder.append(" pv WHERE pv.");
        queryBuilder.append(PkgVersion.PKG_PROPERTY);
        queryBuilder.append("=p AND pv.");
        queryBuilder.append(PkgVersion.REPOSITORY_SOURCE_PROPERTY);
        queryBuilder.append("=:repositorySource)");

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());
        query.setParameter("repositorySource", repositorySource);

        return ImmutableSet.copyOf(context.performQuery(query));
    }

    private VersionCoordinates toVersionCoordinates(org.haiku.pkg.model.PkgVersion version) {
        return new VersionCoordinates(
                version.getMajor(),
                version.getMinor(),
                version.getMicro(),
                version.getPreRelease(),
                version.getRevision());
    }

    /**
     * <p>This method will either find the existing pkg prominence with respect to the
     * repository or will create one and return it.</p>
     */

    public PkgProminence ensurePkgProminence(
            ObjectContext objectContext,
            Pkg pkg,
            Repository repository) {
        return ensurePkgProminence(objectContext, pkg, repository, Prominence.ORDERING_LAST);
    }

    public PkgProminence ensurePkgProminence(
            ObjectContext objectContext,
            Pkg pkg,
            Repository repository,
            Integer ordering) {
        Preconditions.checkArgument(null!=ordering && ordering > 0, "an ordering must be suppied");
        return ensurePkgProminence(
                objectContext, pkg, repository,
                Prominence.getByOrdering(objectContext, ordering).get());
    }

    public PkgProminence ensurePkgProminence(
            ObjectContext objectContext,
            Pkg pkg,
            Repository repository,
            Prominence prominence) {
        Preconditions.checkArgument(null != prominence, "the prominence must be provided");
        Preconditions.checkArgument(null != repository, "the repository must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");
        Optional<PkgProminence> pkgProminenceOptional = pkg.getPkgProminence(repository);

        if(!pkgProminenceOptional.isPresent()) {
            PkgProminence pkgProminence = objectContext.newObject(PkgProminence.class);
            pkg.addToManyTarget(Pkg.PKG_PROMINENCES_PROPERTY, pkgProminence, true);
            pkgProminence.setRepository(repository);
            pkgProminence.setProminence(prominence);
            return pkgProminence;
        }

        return pkgProminenceOptional.get();
    }

    /**
     * <p>Some meta-data can be copied from a master package into the "_devel" package.  This method will see if
     * there is a devel package and will do the copy if there is one.</p>
     */

    public void propagateDataFromPkgToDevelPkg(
            ObjectContext objectContext,
            Pkg pkg) {

        Preconditions.checkArgument(null != objectContext, "the object context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");

        if(!pkg.getName().endsWith(SUFFIX_PKG_DEVELOPMENT)) {
            Optional<Pkg> develPkgOptional = Pkg.getByName(objectContext, pkg.getName() + SUFFIX_PKG_DEVELOPMENT);

            if (develPkgOptional.isPresent()) {
                propagateDataFromPkgToDevelPkg(objectContext, pkg, develPkgOptional.get());
            }
        }
    }

    /**
     * <p>Some meta-data can be copied from the master package to a sub-ordinate package.  An example is a "_devel"
     * package which carries the development files for a master package.  This method will replicate the necessary
     * data out to the subordinate package as necessary.</p>
     */

    private void propagateDataFromPkgToDevelPkg(
            ObjectContext objectContext,
            Pkg pkg,
            Pkg develPkg) {

        Preconditions.checkArgument(null != objectContext, "the object context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");
        Preconditions.checkArgument(null != develPkg, "the development package must be provided");

        for(PkgLocalization pkgLocalization : pkg.getPkgLocalizations()) {
            updatePkgLocalization(
                    objectContext,
                    develPkg,
                    pkgLocalization.getNaturalLanguage(),
                    pkgLocalization.getTitle(),
                    pkgLocalization.getSummary() + SUFFIX_SUMMARY_DEVELOPMENT,
                    pkgLocalization.getDescription());
        }

        for(PkgIcon pkgIcon : pkg.getPkgIcons()) {
            try {
                storePkgIconImage(
                        new ByteArrayInputStream(pkgIcon.getPkgIconImage().get().getData()),
                        pkgIcon.getMediaType(),
                        pkgIcon.getSize(),
                        objectContext,
                        develPkg);
            }
            catch(Throwable th) {
                LOGGER.error(
                        "was unable to update the icon from pkg " + pkg.getName() + " to " + develPkg.getName(),
                        th);
            }
        }

    }

    /**
     * <p>This method will import the package described by the 'pkg' parameter by locating the package and
     * either creating it or updating it as necessary.</p>
     * @param pkg imports into the local database from this package model.
     * @param repositorySourceObjectId the {@link ObjectId} of the source of the package data.
     * @param populatePayloadLength is able to signal to the import process that the length of the package should be
     *                              populated.
     */

    public void importFrom(
            ObjectContext objectContext,
            ObjectId repositorySourceObjectId,
            org.haiku.pkg.model.Pkg pkg,
            boolean populatePayloadLength) {

        Preconditions.checkArgument(null != pkg, "the package must be provided");
        Preconditions.checkArgument(null != repositorySourceObjectId, "the repository source is must be provided");

        RepositorySource repositorySource = RepositorySource.get(objectContext, repositorySourceObjectId);

        if(!repositorySource.getActive()) {
            throw new IllegalStateException("it is not possible to import from a repository source that is not active; " + repositorySource);
        }

        if(!repositorySource.getRepository().getActive()) {
            throw new IllegalStateException("it is not possible to import from a repository that is not active; " + repositorySource.getRepository());
        }

        // first, check to see if the package is there or not.

        Optional<Pkg> persistedPkgOptional = Pkg.getByName(objectContext, pkg.getName());
        Pkg persistedPkg;
        Optional<PkgVersion> persistedLatestExistingPkgVersion = Optional.empty();
        Architecture architecture = Architecture.getByCode(objectContext, pkg.getArchitecture().name().toLowerCase()).get();
        PkgVersion persistedPkgVersion = null;

        if(!persistedPkgOptional.isPresent()) {

            persistedPkg = objectContext.newObject(Pkg.class);
            persistedPkg.setName(pkg.getName());
            persistedPkg.setActive(Boolean.TRUE);
            ensurePkgProminence(objectContext, persistedPkg, repositorySource.getRepository());
            LOGGER.info("the package {} did not exist; will create", pkg.getName());

            // if the package did not exist and is being created now and it is a "_devel" package then
            // it is a good idea to see if there is an already existing package without the "_devel"
            // in the name (as suffix) and copy its fallback localization.  Same applies to iconography.

            if(pkg.getName().endsWith(SUFFIX_PKG_DEVELOPMENT)) {
                String n = pkg.getName();

                Optional<Pkg> rootPkgOptional = Pkg.getByName(
                        objectContext,
                        n.substring(0,n.length() - SUFFIX_PKG_DEVELOPMENT.length()));

                if(rootPkgOptional.isPresent()) {
                    propagateDataFromPkgToDevelPkg(objectContext, rootPkgOptional.get(), persistedPkg);
                }
            }
        }
        else {

            persistedPkg = persistedPkgOptional.get();
            ensurePkgProminence(objectContext, persistedPkg, repositorySource.getRepository());

            // if we know that the package exists then we should look for the version.

            persistedPkgVersion = PkgVersion.getForPkg(
                    objectContext,
                    persistedPkg,
                    repositorySource.getRepository(),
                    architecture,
                    toVersionCoordinates(pkg.getVersion())).orElse(null);

            persistedLatestExistingPkgVersion = getLatestPkgVersionForPkg(
                    objectContext,
                    persistedPkg,
                    repositorySource.getRepository(),
                    Collections.singletonList(architecture));
        }

        if(null==persistedPkgVersion) {

            persistedPkgVersion = objectContext.newObject(PkgVersion.class);
            persistedPkgVersion.setMajor(pkg.getVersion().getMajor());
            persistedPkgVersion.setMinor(pkg.getVersion().getMinor());
            persistedPkgVersion.setMicro(pkg.getVersion().getMicro());
            persistedPkgVersion.setPreRelease(pkg.getVersion().getPreRelease());
            persistedPkgVersion.setRevision(pkg.getVersion().getRevision());
            persistedPkgVersion.setRepositorySource(repositorySource);
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
                    org.haiku.pkg.model.PkgUrlType.HOMEPAGE.name().toLowerCase()).get();

            Optional<PkgVersionUrl> homeUrlOptional = persistedPkgVersion.getPkgVersionUrlForType(pkgUrlType);

            if (null != pkg.getHomePageUrl()) {
                if(homeUrlOptional.isPresent()) {
                    homeUrlOptional.get().setUrl(pkg.getHomePageUrl().getUrl());
                    homeUrlOptional.get().setName(pkg.getHomePageUrl().getName());
                }
                else {
                    PkgVersionUrl persistedPkgVersionUrl = objectContext.newObject(PkgVersionUrl.class);
                    persistedPkgVersionUrl.setUrl(pkg.getHomePageUrl().getUrl());
                    persistedPkgVersionUrl.setName(pkg.getHomePageUrl().getName());
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

    private void fill(ResolvedPkgVersionLocalization result, Pattern pattern, PkgVersionLocalization pvl) {
        if(Strings.isNullOrEmpty(result.getTitle())
                && !Strings.isNullOrEmpty(pvl.getTitle().orElse(null))
                && (null==pattern || pattern.matcher(pvl.getTitle().get()).matches())) {
            result.setTitle(pvl.getTitle().get());
        }

        if(Strings.isNullOrEmpty(result.getSummary())
                && !Strings.isNullOrEmpty(pvl.getSummary().orElse(null))
                && (null==pattern || pattern.matcher(pvl.getSummary().get()).matches()) ) {
            result.setSummary(pvl.getSummary().orElse(null));
        }

        if(Strings.isNullOrEmpty(result.getDescription())
                && !Strings.isNullOrEmpty(pvl.getDescription().orElse(null))
                && (null==pattern || pattern.matcher(pvl.getDescription().get()).matches()) ) {
            result.setDescription(pvl.getDescription().orElse(null));
        }
    }

    private void fill(ResolvedPkgVersionLocalization result, Pattern pattern, PkgLocalization pl) {
        if(Strings.isNullOrEmpty(result.getTitle())
                && !Strings.isNullOrEmpty(pl.getTitle())
                && (null==pattern || pattern.matcher(pl.getTitle()).matches())) {
            result.setTitle(pl.getTitle());
        }

        if(Strings.isNullOrEmpty(result.getSummary())
                && !Strings.isNullOrEmpty(pl.getSummary())
                && (null==pattern || pattern.matcher(pl.getSummary()).matches())) {
            result.setSummary(pl.getSummary());
        }

        if(Strings.isNullOrEmpty(result.getDescription())
                && !Strings.isNullOrEmpty(pl.getDescription())
                && (null==pattern || pattern.matcher(pl.getDescription()).matches())) {
            result.setDescription(pl.getDescription());
        }
    }

    private void fillResolvedPkgVersionLocalization(
            ResolvedPkgVersionLocalization result,
            ObjectContext context,
            PkgVersion pkgVersion,
            Pattern searchPattern,
            NaturalLanguage naturalLanguage) {

        if(!result.hasAll()) {
            Optional<PkgVersionLocalization> pvlNl = PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, naturalLanguage.getCode());

            if (pvlNl.isPresent()) {
                fill(result, searchPattern, pvlNl.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgLocalization> plNl = PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    naturalLanguage.getCode());

            if(plNl.isPresent()) {
                fill(result, searchPattern, plNl.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgVersionLocalization> pvlEn = PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, NaturalLanguage.CODE_ENGLISH);

            if(pvlEn.isPresent()) {
                fill(result, searchPattern, pvlEn.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgLocalization> plEn = PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    NaturalLanguage.CODE_ENGLISH);

            if(plEn.isPresent()) {
                fill(result, searchPattern, plEn.get());
            }
        }

        if(null!=searchPattern) {
            fillResolvedPkgVersionLocalization(result, context, pkgVersion, null, naturalLanguage);
        }
    }

    /**
     * <p>For a given package version, this method will look at the various levels of localization and fallback
     * options to English and will produce an object that represents the best language options.</p>
     *
     * <p>If the pattern is provided, any localization for the provided natural language will be taken first if
     * it matches, otherwise the english version will be tried.</p>
     */

    public ResolvedPkgVersionLocalization resolvePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            Pattern searchPattern,
            NaturalLanguage naturalLanguage) {
        ResolvedPkgVersionLocalization result = new ResolvedPkgVersionLocalization();
        fillResolvedPkgVersionLocalization(result,context,pkgVersion,searchPattern,naturalLanguage);
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

        Preconditions.checkArgument(null != pkg, "the package must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the naturallanguage must be provided");

        if(null!=title) {
            title = title.trim();
        }

        if(null!=summary) {
            summary = summary.trim();
        }

        if(null!=description) {
            description = description.trim();
        }

        // was using the static method, but won't work with temporary objects.
        Optional<PkgLocalization> pkgLocalizationOptional = pkg.getPkgLocalization(naturalLanguage);

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

        // riding off the back of this, if there is a "_devel" package of the same name
        // then its localization should be configured at the same time.

        propagateDataFromPkgToDevelPkg(context, pkg);

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

        Preconditions.checkArgument(null != naturalLanguage, "the natural language must be provided");

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

    public String createHpkgDownloadUrl(PkgVersion pkgVersion) {
        URL packagesBaseUrl = pkgVersion.getRepositorySource().getPackagesBaseURL();

        if(ImmutableSet.of("http","https").contains(packagesBaseUrl.getProtocol())) {
            return pkgVersion.getHpkgURL().toString();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(URL_SEGMENT_PKGDOWNLOAD);
        pkgVersion.appendPathSegments(builder);
        builder.path("package.hpkg");
        return builder.build().toUriString();
    }

    /**
     * <p>This method will update the {@link PkgCategory} set in the
     * nominated {@link Pkg} such that the supplied set are the
     * categories for the package.  It will do this by adding and removing relationships between the package
     * and the categories.</p>
     * @return true if a change was made.
     */

    public boolean updatePkgCategories(ObjectContext context, Pkg pkg, List<PkgCategory> pkgCategories) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");
        Preconditions.checkArgument(null != pkgCategories, "the pkg categories must be provided");

        pkgCategories = new ArrayList<>(pkgCategories);
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

        Preconditions.checkArgument(null != serverRuntime, "the server runtime must be provided");
        Preconditions.checkArgument(null != pkgVersionOid, "the pkg version oid must be provided");
        Preconditions.checkArgument(pkgVersionOid.getEntityName().equals(PkgVersion.class.getSimpleName()), "the oid must reference PkgVersion");

        int attempts = 3;

        while(true) {
            ObjectContext contextEdit = serverRuntime.getContext();
            PkgVersion pkgVersionEdit = ((List<PkgVersion>) contextEdit.performQuery(new ObjectIdQuery(pkgVersionOid)))
                    .stream()
                    .collect(SingleCollector.single());
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
