/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

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
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgUrlType;
import org.haikuos.haikudepotserver.pkg.model.BadPkgIconException;
import org.haikuos.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.ImageHelper;
import org.haikuos.haikudepotserver.support.cayenne.LikeHelper;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * <p>This service undertakes non-trivial operations on packages.</p>
 */

@Service
public class PkgService {

    protected static Logger logger = LoggerFactory.getLogger(PkgService.class);

    protected static int SCREENSHOT_SIDE_LIMIT = 1500;

    private ImageHelper imageHelper = new ImageHelper();

    // ------------------------------
    // SEARCH

    public List<Pkg> search(ObjectContext context, PkgSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);
        Preconditions.checkNotNull(search.getArchitectures());
        Preconditions.checkState(!search.getArchitectures().isEmpty());

        List<String> pkgNames;

        // using jpql because of need to get out raw rows for the pkg name.

        {
            StringBuilder queryBuilder = new StringBuilder();
            List<Object> parameters = Lists.newArrayList();
            List<Architecture> architecturesList = Lists.newArrayList(search.getArchitectures());

            queryBuilder.append("SELECT DISTINCT pv.pkg.name FROM PkgVersion pv WHERE");

            queryBuilder.append(" (");

            for(int i=0; i < architecturesList.size(); i++) {
                if(0!=i) {
                    queryBuilder.append(" OR");
                }

                queryBuilder.append(String.format(" pv.architecture.code = ?%d",parameters.size()+1));
                parameters.add(architecturesList.get(i).getCode());
            }

            queryBuilder.append(")");

            if(!search.getIncludeInactive()) {
                queryBuilder.append(" AND");
                queryBuilder.append(" pv.active = true");
                queryBuilder.append(" AND");
                queryBuilder.append(" pv.pkg.active = true");
            }

            if(!Strings.isNullOrEmpty(search.getExpression())) {
                queryBuilder.append(" AND");
                queryBuilder.append(String.format(" pv.pkg.name LIKE ?%d",parameters.size()+1));
                parameters.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
            }

            queryBuilder.append(" ORDER BY pv.pkg.name ASC");

            EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

            for(int i=0;i<parameters.size();i++) {
                query.setParameter(i+1,parameters.get(i));
            }

            // [apl 13.nov.2013]
            // There seems to be a problem with the resolution of "IN" parameters; it doesn't seem to handle
            // the collection in the parameter very well.  See EJBQLConditionTranslator.processParameter(..)
            // Seems to be a problem if it is a data object or a scalar.

//            queryBuilder.append("SELECT DISTINCT pv.pkg.name FROM PkgVersion pv WHERE");
//            //queryBuilder.append(" pv.architecture IN (:architectures)");
//            queryBuilder.append(" pv.architecture.code IN (:architectureCodes)");
//            queryBuilder.append(" AND");
//            queryBuilder.append(" pv.active=true");
//            queryBuilder.append(" AND");
//            queryBuilder.append(" pv.pkg.active=true");
//
//            if(!Strings.isNullOrEmpty(search.getExpression())) {
//                queryBuilder.append(" AND");
//                queryBuilder.append(" pv.pkg.name LIKE :pkgNameLikeExpression");
//            }
//
//            queryBuilder.append(" ORDER BY pv.pkg.name ASC");
//
//            EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());
//
//            //query.setParameter("architectures", search.getArchitectures());
//            query.setParameter("architectureCodes", Iterables.transform(
//                    search.getArchitectures(),
//                    new Function<Architecture, String>() {
//                        @Override
//                        public String apply(Architecture architecture) {
//                            return architecture.getCode();
//                        }
//                    }
//            ));
//
//            if(!Strings.isNullOrEmpty(search.getExpression())) {
//                query.setParameter("pkgNameLikeExpression", "%" + LikeHelper.escapeExpression(search.getExpression()) + "%");
//            }

            query.setFetchOffset(search.getOffset());
            query.setFetchLimit(search.getLimit());

            pkgNames = (List<String>) context.performQuery(query);
        }

        List<Pkg> pkgs = Collections.emptyList();

        if(0!=pkgNames.size()) {

            SelectQuery query = new SelectQuery(Pkg.class, ExpressionFactory.inExp(Pkg.NAME_PROPERTY, pkgNames));

            pkgs = Lists.newArrayList(context.performQuery(query));

            // repeat the sort of the main query to get the packages back into order again.

            Collections.sort(pkgs, new Comparator<Pkg>() {
                @Override
                public int compare(Pkg o1, Pkg o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
        }

        return pkgs;
    }

    // ------------------------------
    // ICONS

    private void writeGenericIconImage(
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
     * <p>This method will write the package's icon to the supplied output stream.  If there is no icon stored
     * for the package then a generic icon will be provided instead.</p>
     */

    public void writePkgIconImage(
            OutputStream output,
            ObjectContext context,
            Pkg pkg,
            int size) throws IOException {

        Preconditions.checkNotNull(output);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkState(16==size||32==size);

        Optional<PkgIconImage> pkgIconImageOptional = pkg.getPkgIconImage(
                MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get(),
                size);

        if(pkgIconImageOptional.isPresent()) {
            output.write(pkgIconImageOptional.get().getData());
        }

        writeGenericIconImage(output, size);
    }

    /**
     * <p>This method will write the PNG data supplied in the input to the package as its icon.  Note that the icon
     * must comply with necessary characteristics; for example it must be either 16 or 32 pixels along both its sides.
     * If it is non-compliant then an instance of {@link org.haikuos.haikudepotserver.pkg.model.BadPkgIconException} will be thrown.</p>
     */

    public void storePkgIconImage(
            InputStream input,
            int expectedSize,
            ObjectContext context,
            Pkg pkg) throws IOException, BadPkgIconException {

        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkState(16==expectedSize||32==expectedSize);

        byte[] pngData = ByteStreams.toByteArray(input);
        ImageHelper.Size size =  imageHelper.derivePngSize(pngData);

        // check that the file roughly looks like PNG and that the size can be
        // parsed and that the size fits the requirements for the icon.

        if(null==size || (!size.areSides(16) && !size.areSides(32))) {
            logger.warn("attempt to set the package icon for package {}, but the size was not able to be established; either it is not a valid png image or the size of the png image is not appropriate",pkg.getName());
            throw new BadPkgIconException();
        }

        if(expectedSize != size.height && expectedSize != size.width) {
            logger.warn("attempt to set the package icon for package {}, but the size was note the expected {}px",pkg.getName(),expectedSize);
            throw new BadPkgIconException();
        }

        MediaType png = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get();
        Optional<PkgIconImage> pkgIconImageOptional = pkg.getPkgIconImage(png,size.width);
        PkgIconImage pkgIconImage = null;

        if(pkgIconImageOptional.isPresent()) {
            pkgIconImage = pkgIconImageOptional.get();
        }
        else {
            PkgIcon pkgIcon = context.newObject(PkgIcon.class);
            pkg.addToManyTarget(Pkg.PKG_ICONS_PROPERTY, pkgIcon, true);
            pkgIcon.setMediaType(png);
            pkgIcon.setSize(size.width);
            pkgIconImage = context.newObject(PkgIconImage.class);
            pkgIcon.addToManyTarget(PkgIcon.PKG_ICON_IMAGES_PROPERTY, pkgIconImage, true);
        }

        pkgIconImage.setData(pngData);
        pkg.setModifyTimestamp(new java.util.Date());

        logger.info("the icon {}px for package {} has been updated", size.width, pkg.getName());
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
            ImageIO.write(scaledBufferedImage, "PNG", output);
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

        byte[] pngData = ByteStreams.toByteArray(input);
        ImageHelper.Size size =  imageHelper.derivePngSize(pngData);

        // check that the file roughly looks like PNG and the size is something
        // reasonable.

        if(size.height > SCREENSHOT_SIDE_LIMIT || size.width > SCREENSHOT_SIDE_LIMIT) {
            logger.warn("attempt to store a screenshot image that is too large; "+size.toString());
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
            persistedPkgVersion.setArchitecture(Architecture.getByCode(
                    objectContext,
                    pkg.getArchitecture().name().toLowerCase()).get());
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
                PkgVersionLocalization persistedPkgVersionLocalization = objectContext.newObject(PkgVersionLocalization.class);
                persistedPkgVersionLocalization.setDescription(pkg.getDescription());
                persistedPkgVersionLocalization.setSummary(pkg.getSummary());
                persistedPkgVersionLocalization.setPkgVersion(persistedPkgVersion);
            }

            logger.info("the version {} of package {} did not exist; will create", pkg.getVersion().toString(), pkg.getName());
        }

        logger.info("have processed package {}",pkg.toString());

    }


}
