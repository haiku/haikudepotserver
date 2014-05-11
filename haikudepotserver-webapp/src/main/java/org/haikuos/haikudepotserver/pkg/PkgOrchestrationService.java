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
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.model.BadPkgIconException;
import org.haikuos.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.pkg.model.SizeLimitReachedException;
import org.haikuos.haikudepotserver.support.ImageHelper;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.haikuos.haikudepotserver.support.VersionCoordinatesComparator;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
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
import java.util.Collections;
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

    // [apl 11.may.2014]
    // SelectQuery has no means of getting a count.  This is a bit of an annoying limitation, but can be worked around
    // by using EJBQL.  However converting from an Expression to EJBQL has a problem (see CAY-1932) so for the time
    // being, just use EJBQL directly by assembling strings and convert back later.

    // NOTE; raw EJBQL can be replaced with Expressions once CAY-1932 is fixed.
    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            PkgSearchSpecification search) {

        Preconditions.checkNotNull(parameterAccumulator);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);
        Preconditions.checkNotNull(search.getArchitecture());
        Preconditions.checkState(null==search.getDaysSinceLatestVersion() || search.getDaysSinceLatestVersion().intValue() > 0);

        List<String> whereExpressions = Lists.newArrayList();

        whereExpressions.add("pv." + PkgVersion.IS_LATEST_PROPERTY + " = true");

        whereExpressions.add("(pv." + PkgVersion.ARCHITECTURE_PROPERTY + " = ?" + (parameterAccumulator.size() + 1) + " OR pv." + PkgVersion.ARCHITECTURE_PROPERTY + " = ?" + (parameterAccumulator.size() + 2) + ")");
        parameterAccumulator.add(search.getArchitecture());
        parameterAccumulator.add(Architecture.getByCode(context, Architecture.CODE_ANY).get());

        if(!search.getIncludeInactive()) {
            whereExpressions.add("pv." + PkgVersion.ACTIVE_PROPERTY + " = true");
            whereExpressions.add("pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.ACTIVE_PROPERTY + " = true");
        }

        if(null!=search.getDaysSinceLatestVersion()) {
            parameterAccumulator.add(DateTime.now().minusDays(search.getDaysSinceLatestVersion().intValue()).toDate());
            whereExpressions.add("pv." + PkgVersion.CREATE_TIMESTAMP_PROPERTY + " >= ?" + parameterAccumulator.size());
        }

        if(!Strings.isNullOrEmpty(search.getExpression())) {

            switch(search.getExpressionType()) {

                case CONTAINS:
                    parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                    whereExpressions.add("LOWER(pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY + ") LIKE ?" + parameterAccumulator.size() + " ESCAPE '|'");
                    break;

                default:
                    throw new IllegalStateException("unsupported expression type; " + search.getExpressionType());

            }
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

    // NOTE; raw EJBQL can be replaced with Expressions once CAY-1932 is fixed.
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

                case NAME: // gets added anyway...
                    break;

                default:
                    throw new IllegalStateException("unhandled sort ordering; " + search.getSortOrdering());

            }
        }

        orderExpressions.add("pv." + PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY + " ASC");

        return Joiner.on(",").join(orderExpressions);
    }

    // NOTE; raw EJBQL can be replaced with Expressions once CAY-1932 is fixed.  Until then, the prefetch node
    // tree will be ignored.
    public List<PkgVersion> search(
            ObjectContext context,
            PkgSearchSpecification search,
            PrefetchTreeNode prefetchTreeNode) {

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

    // NOTE; raw EJBQL can be replaced with Expressions once CAY-1932 is fixed.
    public long total(
            ObjectContext context,
            PkgSearchSpecification search) {

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


// WILL WORK AFTER CAY-1932 IS IMPLEMENTED!
//    private SelectQuery prepare(
//            ObjectContext context,
//            PkgSearchSpecification search) {
//
//        Preconditions.checkNotNull(search);
//        Preconditions.checkNotNull(context);
//        Preconditions.checkState(search.getOffset() >= 0);
//        Preconditions.checkState(search.getLimit() > 0);
//        Preconditions.checkNotNull(search.getArchitecture());
//        Preconditions.checkState(null==search.getDaysSinceLatestVersion() || search.getDaysSinceLatestVersion().intValue() > 0);
//
//        List<Expression> expressions = Lists.newArrayList();
//
//        expressions.add(ExpressionFactory.matchExp(PkgVersion.IS_LATEST_PROPERTY, Boolean.TRUE));
//
//        expressions.add(
//                ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, search.getArchitecture())
//                        .orExp(ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, Architecture.getByCode(context, Architecture.CODE_ANY).get())));
//
//        if(!search.getIncludeInactive()) {
//            expressions.add(ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE));
//            expressions.add(ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY + "." + Pkg.ACTIVE_PROPERTY, Boolean.TRUE));
//        }
//
//        if(null!=search.getDaysSinceLatestVersion()) {
//            expressions.add(ExpressionFactory.greaterOrEqualExp(
//                    PkgVersion.CREATE_TIMESTAMP_PROPERTY,
//                    DateTime.now().minusDays(search.getDaysSinceLatestVersion().intValue()).toDate()));
//        }
//
//        if(!Strings.isNullOrEmpty(search.getExpression())) {
//     switch(search.getExpressionType()) { // TODO!
//            expressions.add(ExpressionFactory.likeIgnoreCaseExp(
//                    PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY,
//                    "%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%"));
//        }
//
//        if(null!=search.getPkgCategory()) {
//            expressions.add(ExpressionFactory.likeExp(
//                    PkgVersion.PKG_PROPERTY + "." + Pkg.PKG_PKG_CATEGORIES_PROPERTY + "." + PkgPkgCategory.PKG_CATEGORY_PROPERTY,
//                    search.getPkgCategory()));
//        }
//
//        if(null!=search.getPkgNames()) {
//            expressions.add(ExpressionFactory.inExp(
//                    PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY,
//                    search.getPkgNames()));
//        }
//
//        return new SelectQuery(PkgVersion.class, ExpressionHelper.andAll(expressions));
//    }
//
//    /**
//     * <p>This performs a search on the packages.  Note that the prefetch tree node that is supplied is relative to
//     * the package version.</p>
//     */
//
//    public List<PkgVersion> search(
//            ObjectContext context,
//            PkgSearchSpecification search,
//            PrefetchTreeNode prefetchTreeNode) {
//
//        if(null!=search.getPkgNames() && search.getPkgNames().isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        SelectQuery query = prepare(context, search);
//
//        if(null!=search.getSortOrdering()) {
//
//            switch (search.getSortOrdering()) {
//
//                case VERSIONVIEWCOUNTER:
//                    query.addOrdering(new Ordering(PkgVersion.VIEW_COUNTER_PROPERTY, SortOrder.DESCENDING));
//                    break;
//
//                case VERSIONCREATETIMESTAMP:
//                    query.addOrdering(new Ordering(PkgVersion.CREATE_TIMESTAMP_PROPERTY, SortOrder.DESCENDING));
//                    break;
//
//                case NAME: // gets added anyway...
//                    break;
//
//                default:
//                    throw new IllegalStateException("unhandled sort ordering; " + search.getSortOrdering());
//
//            }
//        }
//
//        query.addOrdering(new Ordering(PkgVersion.PKG_PROPERTY + "." + Pkg.NAME_PROPERTY, SortOrder.ASCENDING));
//
//        query.setFetchLimit(search.getLimit());
//        query.setFetchOffset(search.getOffset());
//
//        if(null==prefetchTreeNode) {
//            prefetchTreeNode = new PrefetchTreeNode();
//        }
//
//        // we always want to get the package for a given version
//        prefetchTreeNode.addPath(PkgVersion.PKG_PROPERTY);
//
//        query.setPrefetchTree(prefetchTreeNode);
//
//        //noinspection unchecked
//        return (List<PkgVersion>) context.performQuery(query);
//    }
//
//    /**
//     * <p>This returns the total that would be returned from the search specification ignoring offset and max.  It
//     * is a bit awful that the {@link org.apache.cayenne.query.SelectQuery} has no native mechanic to get the
//     * count for a query, but the EJBQL does and it seems to be relatively easy to get from the Expression of a
//     * to EJBQL statements.</p>
//     */
//
//    public long total(
//            ObjectContext context,
//            PkgSearchSpecification search) {
//
//        if(null!=search.getPkgNames() && search.getPkgNames().isEmpty()) {
//            return 0L;
//        }
//
//        SelectQuery query = prepare(context, search);
//        List<Object> parameters = Lists.newArrayList();
//        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(pv) FROM PkgVersion AS pv WHERE " + query.getQualifier().toEJBQL(parameters,"pv"));
//
//        for(int i=0;i<parameters.size();i++) {
//            ejbQuery.setParameter(i+1, parameters.get(i));
//        }
//
//        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(ejbQuery);
//
//        switch(result.size()) {
//            case 1:
//                return result.get(0).longValue();
//
//            default:
//                throw new IllegalStateException("expected 1 row from count query, but got "+result.size());
//        }
//    }

    // ------------------------------
    // ICONS

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
        Optional<PkgIcon> pkgIconOptional;
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

        logger.info("a screenshot #{} has been added to package {} ({})", ordering, pkg.getName(), screenshot.getCode());

        return screenshot;
    }

    // ------------------------------
    // IMPORT

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
                            .andExp(ExpressionHelper.toExpression(toVersionCoordinates(pkg.getVersion())))
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

            // now possibly switch the latest flag over to the new one from the old one.

            if(persistedLatestExistingPkgVersion.isPresent()) {
                VersionCoordinatesComparator versionCoordinatesComparator = new VersionCoordinatesComparator();
                VersionCoordinates persistedPkgVersionCoords = persistedPkgVersion.toVersionCoordinates();
                VersionCoordinates persistedLatestExistingPkgVersionCoords = persistedLatestExistingPkgVersion.get().toVersionCoordinates();

                if(versionCoordinatesComparator.compare(
                        persistedPkgVersionCoords,
                        persistedLatestExistingPkgVersionCoords) > 0) {
                    persistedPkgVersion.setIsLatest(true);
                    persistedLatestExistingPkgVersion.get().setIsLatest(false);
                }
                else {
                    logger.warn(
                            "imported a package version {} which is older or the same as the existing {}",
                            persistedPkgVersionCoords,
                            persistedLatestExistingPkgVersionCoords);
                }
            }
            else {
                persistedPkgVersion.setIsLatest(true);
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


}
