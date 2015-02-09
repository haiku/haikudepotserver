/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.model.*;
import org.haikuos.haikudepotserver.support.*;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
import org.haikuos.haikudepotserver.support.cayenne.LikeHelper;
import org.imgscalr.Scalr;
import org.joda.time.DateTime;
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

    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            PkgSearchSpecification search) {

        Preconditions.checkNotNull(parameterAccumulator);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(null!=search.getArchitectures()&&!search.getArchitectures().isEmpty());
        Preconditions.checkState(null==search.getDaysSinceLatestVersion() || search.getDaysSinceLatestVersion().intValue() > 0);

        List<String> whereExpressions = Lists.newArrayList();

        whereExpressions.add("pv." + PkgVersion.IS_LATEST_PROPERTY + " = true");

        {
            List<String> architectureWhereExpressions = Lists.newArrayList();

            for(Architecture architecture : search.getArchitectures()) {
                architectureWhereExpressions.add("(pv." + PkgVersion.ARCHITECTURE_PROPERTY + " = ?" + (parameterAccumulator.size() + 1)+")");
                parameterAccumulator.add(architecture);
            }

            whereExpressions.add("(" + Joiner.on(" OR ").join(architectureWhereExpressions) + ")");
        }

        if(!search.getIncludeInactive()) {
            whereExpressions.add("pv." + PkgVersion.ACTIVE_PROPERTY + " = true");
            whereExpressions.add("pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.ACTIVE_PROPERTY + " = true");
        }

        if(null!=search.getDaysSinceLatestVersion()) {
            parameterAccumulator.add(DateTime.now().minusDays(search.getDaysSinceLatestVersion().intValue()).toDate());
            whereExpressions.add("pv." + PkgVersion.CREATE_TIMESTAMP_PROPERTY + " >= ?" + parameterAccumulator.size());
        }

        if(!Strings.isNullOrEmpty(search.getExpression())) {

            StringBuilder expressionWhereAssembly = new StringBuilder();
            expressionWhereAssembly.append("(");

            switch(search.getExpressionType()) {

                case CONTAINS:
                    parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                    expressionWhereAssembly.append("LOWER(pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY + ") LIKE ?" + parameterAccumulator.size() + " ESCAPE '|'");
                    break;

                default:
                    throw new IllegalStateException("unsupported expression type; " + search.getExpressionType());

            }

            // [apl 29.jun.2014] search in the package version descriptions as well.  In this case, try to find the
            // latest package version on the package.  If this has a localization for the supplied natural language
            // then search in that; otherwise drop back to english.

            expressionWhereAssembly.append(" OR ");

            // specified natural language localized description
            {
                expressionWhereAssembly.append("EXISTS(SELECT pvl1 FROM ");
                expressionWhereAssembly.append(PkgVersionLocalization.class.getSimpleName());
                expressionWhereAssembly.append(" pvl1 WHERE pvl1." + PkgVersionLocalization.PKG_VERSION_PROPERTY + " = pv");
                parameterAccumulator.add(search.getNaturalLanguage());
                expressionWhereAssembly.append(" AND pvl1." + PkgVersionLocalization.NATURAL_LANGUAGE_PROPERTY + " = ?" + parameterAccumulator.size());

                switch (search.getExpressionType()) {

                    case CONTAINS:
                        parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                        expressionWhereAssembly.append(" AND LOWER(pvl1." + PkgVersionLocalization.SUMMARY_PROPERTY + ") LIKE ?" + parameterAccumulator.size() + " ESCAPE '|'");
                        break;

                    default:
                        throw new IllegalStateException("unsupported expression type; " + search.getExpressionType());

                }
                expressionWhereAssembly.append(")");
            }

            if(!search.getNaturalLanguage().getCode().equals(NaturalLanguage.CODE_ENGLISH)) {
                expressionWhereAssembly.append(" OR ");

                // fallback english localized description if necessary

                {
                    expressionWhereAssembly.append("(");

                    {
                        expressionWhereAssembly.append("NOT EXISTS(SELECT pvl2 FROM ");
                        expressionWhereAssembly.append(PkgVersionLocalization.class.getSimpleName());
                        expressionWhereAssembly.append(" pvl2 WHERE pvl2." + PkgVersionLocalization.PKG_VERSION_PROPERTY + " = pv");
                        parameterAccumulator.add(search.getNaturalLanguage());
                        expressionWhereAssembly.append(" AND pvl2." + PkgVersionLocalization.NATURAL_LANGUAGE_PROPERTY + " = ?" + parameterAccumulator.size());
                        expressionWhereAssembly.append(")");
                    }

                    expressionWhereAssembly.append(" AND ");

                    {
                        expressionWhereAssembly.append("EXISTS(SELECT pvl3 FROM ");
                        expressionWhereAssembly.append(PkgVersionLocalization.class.getSimpleName());
                        expressionWhereAssembly.append(" pvl3 WHERE pvl3." + PkgVersionLocalization.PKG_VERSION_PROPERTY + " = pv");
                        parameterAccumulator.add(NaturalLanguage.CODE_ENGLISH);
                        expressionWhereAssembly.append(" AND pvl3." + PkgVersionLocalization.NATURAL_LANGUAGE_PROPERTY + "." + NaturalLanguage.CODE_PROPERTY + " = ?" + parameterAccumulator.size());

                        switch (search.getExpressionType()) {

                            case CONTAINS:
                                parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                                expressionWhereAssembly.append(" AND LOWER(pvl3." + PkgVersionLocalization.SUMMARY_PROPERTY + ") LIKE ?" + parameterAccumulator.size() + " ESCAPE '|'");
                                break;

                            default:
                                throw new IllegalStateException("unsupported expression type; " + search.getExpressionType());

                        }
                        expressionWhereAssembly.append(")");
                    }

                    expressionWhereAssembly.append(")");

                }
            }

            expressionWhereAssembly.append(")");
            whereExpressions.add(expressionWhereAssembly.toString());
        }

        if(null!=search.getPkgCategory()) {
            parameterAccumulator.add(search.getPkgCategory());
            whereExpressions.add("pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.PKG_PKG_CATEGORIES_PROPERTY + "." + PkgPkgCategory.PKG_CATEGORY_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if(null!=search.getPkgNames()) {
            if(search.getPkgNames().isEmpty()) {
                throw new IllegalStateException("list of pkg names is empty; not able to produce ejbql expression");
            }

            List<String> inParameterList = Lists.newArrayList();

            for(int j=0;j<search.getPkgNames().size();j++) {
                parameterAccumulator.add(search.getPkgNames().get(j));
                inParameterList.add("?" + parameterAccumulator.size());
            }

            whereExpressions.add("pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY + " IN (" + Joiner.on(",").join(inParameterList) + ")");
        }

        return Joiner.on(" AND ").join(whereExpressions);
    }

    private String prepareOrderClause(
            ObjectContext context,
            PkgSearchSpecification search) {

        List<String> orderExpressions = Lists.newArrayList();

        if(null!=search.getSortOrdering()) {

            switch (search.getSortOrdering()) {

                case VERSIONVIEWCOUNTER:
                    orderExpressions.add("pv." + PkgVersion.VIEW_COUNTER_PROPERTY + " DESC");
                    break;

                case VERSIONCREATETIMESTAMP:
                    orderExpressions.add("pv." + PkgVersion.CREATE_TIMESTAMP_PROPERTY + " DESC");
                    break;

                case PROMINENCE:
                    orderExpressions.add("pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.PROMINENCE_PROPERTY + "." + Prominence.ORDERING_PROPERTY + " ASC");

                case NAME: // gets added anyway...
                    break;

                default:
                    throw new IllegalStateException("unhandled sort ordering; " + search.getSortOrdering());

            }
        }

        orderExpressions.add("pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY + " ASC");

        return Joiner.on(",").join(orderExpressions);
    }

    public List<PkgVersion> search(
            ObjectContext context,
            PkgSearchSpecification search,
            PrefetchTreeNode prefetchTreeNode) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(search.getNaturalLanguage());
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        if(null!=search.getPkgNames() && search.getPkgNames().isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> parameterAccumulator = Lists.newArrayList();
        String whereClause = prepareWhereClause(parameterAccumulator, context, search);
        String orderClause = prepareOrderClause(context, search);

        EJBQLQuery query = new EJBQLQuery("SELECT pv FROM " + PkgVersion.class.getSimpleName() + " AS pv WHERE " + whereClause + " ORDER BY " + orderClause);

        for(int i=0;i<parameterAccumulator.size();i++) {
            query.setParameter(i+1,parameterAccumulator.get(i));
        }

        query.setFetchLimit(search.getLimit());
        query.setFetchOffset(search.getOffset());

        //noinspection unchecked
        return (List<PkgVersion>) context.performQuery(query);
    }

    public long total(
            ObjectContext context,
            PkgSearchSpecification search) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(search.getNaturalLanguage());

        if(null!=search.getPkgNames() && search.getPkgNames().isEmpty()) {
            return 0L;
        }

        List<Object> parameterAccumulator = Lists.newArrayList();
        String whereClause = prepareWhereClause(parameterAccumulator, context, search);
        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(pv) FROM PkgVersion AS pv WHERE " + whereClause);

        for(int i=0;i<parameterAccumulator.size();i++) {
            ejbQuery.setParameter(i+1, parameterAccumulator.get(i));
        }

        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(ejbQuery);

        switch(result.size()) {
            case 1:
                return result.get(0).longValue();

            default:
                throw new IllegalStateException("expected 1 row from count query, but got "+result.size());
        }
    }

    /**
     * <p>This will be called for each package in the system.</p>
     * @param c is the callback to invoke.
     * @return the quantity of packages processed.
     */

    public long eachPkg(
            ObjectContext context,
            PkgSearchSpecification search,
            Callback<Pkg> c) {

        Preconditions.checkArgument(null!=c, "the callback should be provided to run for each package");
        Preconditions.checkArgument(null!=search, "the search should be provided to specify the packages to iterate over");
        Preconditions.checkArgument(null!=context, "the object context must be provided");

        if(null!=search.getPkgNames() && search.getPkgNames().isEmpty()) {
            return 0L;
        }

        List<Object> parameterAccumulator = Lists.newArrayList();
        String whereClause = prepareWhereClause(parameterAccumulator, context, search);

        StringBuilder ejbql = new StringBuilder();
        ejbql.append("SELECT DISTINCT p FROM Pkg AS p, PkgVersion AS pv WHERE pv.pkg=p AND ");
        ejbql.append(whereClause);
        ejbql.append(" ORDER BY p.name ASC");

        EJBQLQuery query = new EJBQLQuery(ejbql.toString());

        for(int i=0;i<parameterAccumulator.size();i++) {
            query.setParameter(i+1, parameterAccumulator.get(i));
        }

        int offset = 0;

        while(true) {
            query.setFetchLimit(100); // arbitrary -- might need to tune.
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

        final List<String> codes = context.performQuery(query);

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
                    pkg.getSummary(),
                    pkg.getDescription());
        }

        // look back at the previous version of the same package and see if there are localizations.  If there
        // are then replicate those into this version as well, but only if the english variant exists.  Don't do
        // this if the package is being updated instead of created.

        if(persistedLatestExistingPkgVersion.isPresent() && persistedPkgVersion.getObjectId().isTemporary()) {
            int naturalLanguagesReplicated = replicateLocalizationIfEnglishMatches(
                    objectContext,
                    persistedLatestExistingPkgVersion.get(),
                    persistedPkgVersion,
                    NaturalLanguage.getAllExceptEnglish(objectContext),
                    false).size();

            if(0!=naturalLanguagesReplicated) {
                LOGGER.info(
                        "replicated {} natural language localizations when creating new version of package {}",
                        naturalLanguagesReplicated,
                        pkg.getName());
            }
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

    /**
     * <p>This method will update the localization defined in the parameters to this method into the data
     * structure for the package.</p>
     */

    public PkgLocalization updatePkgLocalization(
            ObjectContext context,
            Pkg pkg,
            NaturalLanguage naturalLanguage,
            String title) {

        Preconditions.checkNotNull(naturalLanguage);

        if(null!=title) {
            title = title.trim();
        }

        Optional<PkgLocalization> pkgLocalizationOptional = pkg.getPkgLocalization(naturalLanguage.getCode());

        if(Strings.isNullOrEmpty(title)) {
            if(!pkgLocalizationOptional.isPresent()) {
                pkg.removeToManyTarget(Pkg.PKG_LOCALIZATIONS_PROPERTY, pkgLocalizationOptional.get(), true);
                context.deleteObject(pkgLocalizationOptional.get());
            }

            return null;
        }

        if(!pkgLocalizationOptional.isPresent()) {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(naturalLanguage);
            pkgLocalization.setTitle(title);
            pkg.addToManyTarget(Pkg.PKG_LOCALIZATIONS_PROPERTY, pkgLocalization, true);
            return pkgLocalization;
        }

        pkgLocalizationOptional.get().setTitle(title);
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

        if(summaryNullOrEmpty) {

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

}
