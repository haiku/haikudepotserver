/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.DataObject;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgIconImage;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.haiku.haikudepotserver.pkg.model.*;
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

    private final PkgSupplementModificationService pkgSupplementModificationService;

    public PkgIconServiceImpl(
            RenderedPkgIconRepository renderedPkgIconRepository,
            PkgSupplementModificationService pkgSupplementModificationService,
            PngOptimizationService pngOptimizationService) {
        this.renderedPkgIconRepository = Preconditions.checkNotNull(renderedPkgIconRepository);
        this.pkgSupplementModificationService = Preconditions.checkNotNull(pkgSupplementModificationService);
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
    public void removePkgIcon(ObjectContext context, PkgSupplementModificationAgent agent, PkgSupplement pkgSupplement) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkgSupplement, "the package must be supplied");
        context.deleteObjects(deriveDataObjectsToDelete(pkgSupplement.getPkgIcons()));

        pkgSupplement.setModifyTimestamp();
        pkgSupplement.setIconModifyTimestamp();

        pkgSupplementModificationService.appendModification(
                context,
                pkgSupplement,
                agent,
                String.format("remove icon for pkg [%s]", pkgSupplement.getBasePkgName())
        );

        renderedPkgIconRepository.evict(context, pkgSupplement);
    }

    @Override
    public PkgIcon storePkgIconImage(
            InputStream input,
            MediaType mediaType,
            Integer expectedSize,
            ObjectContext context,
            PkgSupplementModificationAgent agent,
            PkgSupplement pkgSupplement) throws IOException, BadPkgIconException {

        Preconditions.checkArgument(null != context, "the context is not supplied");
        Preconditions.checkArgument(null != input, "the input must be provided");
        Preconditions.checkArgument(null != mediaType, "the mediaType must be provided");
        Preconditions.checkArgument(null != pkgSupplement, "the pkgSupplement must be provided");

        InputStream boundedInputStream = new org.apache.commons.io.input.BoundedInputStream.Builder()
                .setInputStream(input)
                .setMaxCount(ICON_SIZE_LIMIT)
                .setPropagateClose(false)
                .get();
        byte[] imageData = ByteStreams.toByteArray(boundedInputStream);

        Optional<PkgIcon> pkgIconOptional;
        Integer size = null;

        switch (mediaType.getCode()) {
            case MediaType.MEDIATYPE_PNG -> {
                ImageHelper.Size pngSize = imageHelper.derivePngSize(imageData);
                if (null == pngSize) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid;"
                            + "it is not a valid png image", pkgSupplement.getBasePkgName());
                    throw new BadPkgIconException("invalid png");
                }
                if (!pngSize.areSides(16) && !pngSize.areSides(32) && !pngSize.areSides(64)) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size was invalid; "
                                    + "it must be either 32x32 or 16x16 px, but was {}",
                            pkgSupplement.getBasePkgName(), pngSize.toString());
                    throw new BadPkgIconException("non-square sizing or unexpected sizing");
                }
                if (null != expectedSize && !pngSize.areSides(expectedSize)) {
                    LOGGER.warn("attempt to set the bitmap (png) package icon for package {}, but the size did not "
                            + " match the expected size", pkgSupplement.getBasePkgName());
                    throw new BadPkgIconException("size of image was not as expected");
                }
                size = pngSize.width;
                pkgIconOptional = pkgSupplement.tryGetPkgIcon(mediaType, pngSize.width);
            }
            case MediaType.MEDIATYPE_HAIKUVECTORICONFILE -> {
                if (!imageHelper.looksLikeHaikuVectorIconFormat(imageData)) {
                    LOGGER.warn("attempt to set the vector (hvif) package icon for package {}, but the data does not "
                            + "look like hvif", pkgSupplement.getBasePkgName());
                    throw new BadPkgIconException();
                }
                pkgIconOptional = pkgSupplement.tryGetPkgIcon(mediaType, null);
            }
            default -> throw new IllegalStateException("unhandled media type; " + mediaType.getCode());
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

            pkgSupplementModificationService.appendModification(
                    context,
                    pkgSupplement,
                    agent,
                    String.format("add icon for pkg [%s]; size [%d]; media type [%s]; sha256 [%s]",
                            pkgSupplement.getBasePkgName(),
                            expectedSize,
                            mediaType.getCode(),
                            Hashing.sha256().hashBytes(imageData)
                    )
            );
        }
        else {
            LOGGER.info("no change to package icon for [{}] ", pkgSupplement.getBasePkgName());
        }

        return pkgIconOptional.orElseThrow(IllegalStateException::new);
    }

    private List<MediaType> getInUsePkgIconMediaTypes(final ObjectContext context) {
        return ObjectSelect
                .query(PkgIcon.class)
                .column(PkgIcon.MEDIA_TYPE)
                .distinct()
                .orderBy(PkgIcon.MEDIA_TYPE.dot(MediaType.CODE).asc())
                .select(context);
    }

    private List<Integer> getInUsePkgIconSizes(ObjectContext context, MediaType mediaType) {
        return ObjectSelect
                .query(PkgIcon.class)
                .where(PkgIcon.MEDIA_TYPE.eq(mediaType))
                .column(PkgIcon.SIZE)
                .distinct()
                .orderBy(PkgIcon.SIZE.asc())
                .select(context);
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
