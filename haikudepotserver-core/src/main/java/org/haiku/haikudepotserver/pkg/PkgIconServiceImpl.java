/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.DataObject;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.compress.utils.BoundedInputStream;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgIconImage;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.haiku.haikudepotserver.pkg.model.BadPkgIconException;
import org.haiku.haikudepotserver.pkg.model.PkgIconConfiguration;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PkgIconServiceImpl implements PkgIconService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(PkgIconServiceImpl.class);

    @SuppressWarnings("FieldCanBeLocal")
    private static final int ICON_SIZE_LIMIT = 100 * 1024; // 100k

    private final RenderedPkgIconRepository renderedPkgIconRepository;
    private final PngOptimizationService pngOptimizationService;
    private final ImageHelper imageHelper;

    public PkgIconServiceImpl(
            RenderedPkgIconRepository renderedPkgIconRepository,
            PngOptimizationService pngOptimizationService) {
        this.renderedPkgIconRepository = Preconditions.checkNotNull(renderedPkgIconRepository);
        this.pngOptimizationService = Preconditions.checkNotNull(pngOptimizationService);
        imageHelper = new ImageHelper();
    }

    @Override
    public Date getLastPkgIconModifyTimestampSecondAccuracy(ObjectContext context) {
        Date result = ObjectSelect
                .query(PkgSupplement.class)
                .max(PkgSupplement.ICON_MODIFY_TIMESTAMP)
                .selectOne(context);

        if (null == result) {
            return new Date(0);
        }

        return result;
    }

    @Override
    public void removePkgIcon(ObjectContext context, PkgSupplement pkgSupplement) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkgSupplement, "the package must be supplied");
        context.deleteObjects(deriveDataObjectsToDelete(pkgSupplement.getPkgIcons()));
        pkgSupplement.setModifyTimestamp();
        pkgSupplement.setIconModifyTimestamp();
        renderedPkgIconRepository.evict(context, pkgSupplement);
    }

    @Override
    public PkgIcon storePkgIconImage(
            InputStream input,
            MediaType mediaType,
            Integer expectedSize,
            ObjectContext context,
            PkgSupplement pkgSupplement) throws IOException, BadPkgIconException {

        Preconditions.checkArgument(null != context, "the context is not supplied");
        Preconditions.checkArgument(null != input, "the input must be provided");
        Preconditions.checkArgument(null != mediaType, "the mediaType must be provided");
        Preconditions.checkArgument(null != pkgSupplement, "the pkgSupplement must be provided");

        byte[] imageData = ByteStreams.toByteArray(new BoundedInputStream(input, ICON_SIZE_LIMIT));

        Optional<PkgIcon> pkgIconOptional;
        Integer size = null;

        switch(mediaType.getCode()) {

            case MediaType.MEDIATYPE_PNG:
                ImageHelper.Size pngSize =  imageHelper.derivePngSize(imageData);

                if(null==pngSize) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid;"
                            + "it is not a valid png image", pkgSupplement.getBasePkgName());
                    throw new BadPkgIconException("invalid png");
                }

                if(!pngSize.areSides(16) && !pngSize.areSides(32) && !pngSize.areSides(64)) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; "
                            + "it must be either 32x32 or 16x16 px, but was {}",
                            pkgSupplement.getBasePkgName(), pngSize.toString());
                    throw new BadPkgIconException("non-square sizing or unexpected sizing");
                }

                if(null!=expectedSize && !pngSize.areSides(expectedSize)) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size did not "
                            + " match the expected size", pkgSupplement.getBasePkgName());
                    throw new BadPkgIconException("size of image was not as expected");
                }

                try {
                    imageData = pngOptimizationService.optimize(imageData);
                }
                catch(IOException ioe) {
                    throw new RuntimeException("the png optimization process has failed; ", ioe);
                }

                size = pngSize.width;
                pkgIconOptional = pkgSupplement.tryGetPkgIcon(mediaType, pngSize.width);
                break;

            case MediaType.MEDIATYPE_HAIKUVECTORICONFILE:
                if(!imageHelper.looksLikeHaikuVectorIconFormat(imageData)) {
                    LOGGER.warn("attempt to set the vector (hvif) package icon for package {}, but the data does not "
                            + "look like hvif", pkgSupplement.getBasePkgName());
                    throw new BadPkgIconException();
                }
                pkgIconOptional = pkgSupplement.tryGetPkgIcon(mediaType, null);
                break;

            default:
                throw new IllegalStateException("unhandled media type; "+mediaType.getCode());

        }

        PkgIconImage pkgIconImage;

        if(pkgIconOptional.isPresent()) {
            pkgIconImage = pkgIconOptional.get().getPkgIconImage();
        }
        else {
            PkgIcon pkgIcon = context.newObject(PkgIcon.class);
            pkgSupplement.addToManyTarget(PkgSupplement.PKG_ICONS.getName(), pkgIcon, true);
            pkgIcon.setMediaType(mediaType);
            pkgIcon.setSize(size);
            pkgIconImage = context.newObject(PkgIconImage.class);
            pkgIcon.addToManyTarget(PkgIcon.PKG_ICON_IMAGES.getName(), pkgIconImage, true);
            pkgIconOptional = Optional.of(pkgIcon);
        }

        if (pkgIconImage.getData() == null || !Arrays.equals(pkgIconImage.getData(), imageData)) {
            pkgIconImage.setData(imageData);
            pkgSupplement.setModifyTimestamp();
            pkgSupplement.setIconModifyTimestamp(new java.sql.Timestamp(Clock.systemUTC().millis()));
            renderedPkgIconRepository.evict(context, pkgSupplement);

            if (null != size) {
                LOGGER.info("the icon {}px for package [{}] has been updated", size, pkgSupplement.getBasePkgName());
            } else {
                LOGGER.info("the icon for package [{}] has been updated", pkgSupplement.getBasePkgName());
            }
        }
        else {
            LOGGER.info("no change to package icon for [{}] ", pkgSupplement.getBasePkgName());
        }

        return pkgIconOptional.orElseThrow(IllegalStateException::new);
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
                .map(c -> MediaType.getByCode(context, c))
                .collect(Collectors.toList());

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
                .flatMap((pi) -> Arrays.stream(new DataObject[] {pi.getPkgIconImage(), pi}))
                .collect(Collectors.toList());
    }

}
