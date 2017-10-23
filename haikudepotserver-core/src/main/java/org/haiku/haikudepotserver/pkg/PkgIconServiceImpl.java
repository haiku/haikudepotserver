/*
 * Copyright 2016-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.DataObject;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.io.input.BoundedInputStream;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgIconImage;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.haiku.haikudepotserver.pkg.model.BadPkgIconException;
import org.haiku.haikudepotserver.pkg.model.PkgIconConfiguration;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PkgIconServiceImpl implements PkgIconService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgIconServiceImpl.class);

    @SuppressWarnings("FieldCanBeLocal")
    private static int ICON_SIZE_LIMIT = 100 * 1024; // 100k

    @Resource
    private RenderedPkgIconRepository renderedPkgIconRepository;

    @Resource
    private PngOptimizationService pngOptimizationService;

    @Resource
    private PkgServiceImpl pkgServiceImpl;

    private ImageHelper imageHelper = new ImageHelper();

    @Override
    public Date getLastPkgIconModifyTimestampSecondAccuracy(ObjectContext context) {
        Date result = ObjectSelect
                .query(Pkg.class)
                .where(Pkg.ACTIVE.isTrue())
                .max(Pkg.ICON_MODIFY_TIMESTAMP)
                .selectOne(context);

        if (null == result) {
            return new Date(0);
        }

        return result;
    }

    @Override
    public void removePkgIcon(ObjectContext context, Pkg pkg) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the package must be supplied");

        context.deleteObjects(deriveDataObjectsToDelete(pkg.getPkgIcons()));
        pkg.setModifyTimestamp();
        pkg.setIconModifyTimestamp();

        pkgServiceImpl.tryGetDevelPkg(context, pkg.getName())
                .ifPresent(develPkg -> removePkgIcon(context, develPkg));

    }

    @Override
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

        byte[] imageData = ByteStreams.toByteArray(new BoundedInputStream(input, ICON_SIZE_LIMIT));

        Optional<PkgIcon> pkgIconOptional;
        Integer size = null;

        switch(mediaType.getCode()) {

            case MediaType.MEDIATYPE_PNG:
                ImageHelper.Size pngSize =  imageHelper.derivePngSize(imageData);

                if(null==pngSize) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it is not a valid png image", pkg.getName());
                    throw new BadPkgIconException("invalid png");
                }

                if(!pngSize.areSides(16) && !pngSize.areSides(32) && !pngSize.areSides(64)) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; it must be either 32x32 or 16x16 px, but was {}", pkg.getName(), pngSize.toString());
                    throw new BadPkgIconException("non-square sizing or unexpected sizing");
                }

                if(null!=expectedSize && !pngSize.areSides(expectedSize)) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size did not match the expected size", pkg.getName());
                    throw new BadPkgIconException("size of image was not as expected");
                }

                try {
                    imageData = pngOptimizationService.optimize(imageData);
                }
                catch(IOException ioe) {
                    throw new RuntimeException("the png optimization process has failed; ", ioe);
                }

                size = pngSize.width;
                pkgIconOptional = pkg.getPkgIcon(mediaType, pngSize.width);
                break;

            case MediaType.MEDIATYPE_HAIKUVECTORICONFILE:
                if(!imageHelper.looksLikeHaikuVectorIconFormat(imageData)) {
                    LOGGER.warn("attempt to set the vector (hvif) package icon for package {}, but the data does not look like hvif", pkg.getName());
                    throw new BadPkgIconException();
                }
                pkgIconOptional = pkg.getPkgIcon(mediaType, null);
                break;

            default:
                throw new IllegalStateException("unhandled media type; "+mediaType.getCode());

        }

        PkgIconImage pkgIconImage;

        if(pkgIconOptional.isPresent()) {
            pkgIconImage = pkgIconOptional.get().getPkgIconImage().get();
        }
        else {
            PkgIcon pkgIcon = context.newObject(PkgIcon.class);
            pkg.addToManyTarget(Pkg.PKG_ICONS.getName(), pkgIcon, true);
            pkgIcon.setMediaType(mediaType);
            pkgIcon.setSize(size);
            pkgIconImage = context.newObject(PkgIconImage.class);
            pkgIcon.addToManyTarget(PkgIcon.PKG_ICON_IMAGES.getName(), pkgIconImage, true);
            pkgIconOptional = Optional.of(pkgIcon);
        }

        pkgIconImage.setData(imageData);
        pkg.setModifyTimestamp();
        pkg.setIconModifyTimestamp();
        renderedPkgIconRepository.evict(context, pkg);

        if(null!=size) {
            LOGGER.info("the icon {}px for package {} has been updated", size, pkg.getName());
        }
        else {
            LOGGER.info("the icon for package {} has been updated", pkg.getName());
        }

        PkgIcon pkgIcon = pkgIconOptional.orElseThrow(IllegalStateException::new);
        Optional<Pkg> develPkgOptional = pkgServiceImpl.tryGetDevelPkg(context, pkg.getName());

        if(develPkgOptional.isPresent()) {
            replicatePkgIcon(context, pkgIcon, develPkgOptional.get());
        }

        return pkgIcon;
    }

    private List<MediaType> getInUsePkgIconMediaTypes(final ObjectContext context) {
        EJBQLQuery query = new EJBQLQuery(String.join(" ",
                "SELECT",
                "DISTINCT pi." + PkgIcon.MEDIA_TYPE.getName() + "." + MediaType.CODE.getName(),
                "FROM",
                PkgIcon.class.getSimpleName(),
                "pi"));

        final List<String> codes = (List<String>) context.performQuery(query);

        return codes
                .stream()
                .map(c -> MediaType.getByCode(context, c).get())
                .collect(Collectors.toList());

    }

    @Override
    public PkgIcon replicatePkgIcon(
            ObjectContext context,
            PkgIcon pkgIcon,
            Pkg targetPkg) throws IOException, BadPkgIconException {

        byte[] data = pkgIcon.getPkgIconImage().orElseThrow(IllegalStateException::new).getData();

        return storePkgIconImage(
                new ByteArrayInputStream(data),
                pkgIcon.getMediaType(),
                pkgIcon.getSize(),
                context,
                targetPkg);
    }

    @Override
    public void replicatePkgIcons(
            ObjectContext context,
            Pkg sourcePkg,
            Pkg targetPkg) throws IOException, BadPkgIconException {

        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != sourcePkg, "the source pkg must be supplied");
        Preconditions.checkArgument(null != targetPkg, "the target pkg must be supplied");

        // first remove all of the icons from the target pkg that do not exist in the source.

        List<DataObject> targetPkgIconsDataObjectsToDelete = deriveDataObjectsToDelete(targetPkg.getPkgIcons()
                .stream()
                .filter((tpi) -> !sourcePkg.getPkgIcons()
                        .stream()
                        .anyMatch((spi) -> Objects.equals(spi.getMediaType(), tpi.getMediaType()) &&
                                Objects.equals(spi.getSize(), tpi.getSize()))
                )
                .collect(Collectors.toList()));

        context.deleteObjects(targetPkgIconsDataObjectsToDelete);

        // now merge in those from the source.

        for(PkgIcon pkgIcon : sourcePkg.getPkgIcons()) {
            replicatePkgIcon(context, pkgIcon, targetPkg);
        }
    }

    private List<Integer> getInUsePkgIconSizes(ObjectContext context, MediaType mediaType) {
        EJBQLQuery query = new EJBQLQuery(String.join(" ",
                "SELECT",
                "DISTINCT pi." + PkgIcon.SIZE.getName(),
                "FROM",
                PkgIcon.class.getSimpleName(),
                "pi WHERE pi." + PkgIcon.MEDIA_TYPE.getName(),
                "=",
                ":mediaType"));

        query.setParameter("mediaType", mediaType);

        return (List<Integer>) context.performQuery(query);
    }

    @Override
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

    private List<DataObject> deriveDataObjectsToDelete(List<PkgIcon> pkgIcons) {
        return pkgIcons.stream()
                .flatMap((pi) ->
                        pi.getPkgIconImage()
                                .map((pii) -> (List<DataObject>) ImmutableList.<DataObject>of(pii, pi))
                                .orElse(Collections.singletonList(pi))
                                .stream()
                )
                .collect(Collectors.toList());
    }

}
