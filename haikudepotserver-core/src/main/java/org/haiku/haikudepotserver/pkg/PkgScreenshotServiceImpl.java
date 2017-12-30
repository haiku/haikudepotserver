/*
 * Copyright 2016-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.ObjectContext;
import org.apache.commons.io.input.BoundedInputStream;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshotImage;
import org.haiku.haikudepotserver.dataobjects.auto._PkgScreenshot;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.haiku.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotService;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PkgScreenshotServiceImpl implements PkgScreenshotService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgIconServiceImpl.class);

    private static final HashFunction HASH_FUNCTION = Hashing.sha256();

    private ImageHelper imageHelper = new ImageHelper();

    @Resource
    private PkgService pkgService;

    // these seem like reasonable limits for the size of image data to have to
    // handle in-memory.

    @SuppressWarnings("FieldCanBeLocal")
    private static final int SCREENSHOT_SIDE_LIMIT = 1500;

    @SuppressWarnings("FieldCanBeLocal")
    private static final int SCREENSHOT_SIZE_LIMIT = 2 * 1024 * 1024; // 2MB

    /**
     * <p>This method will write the package's screenshot to the output stream.  It will constrain the output to the
     * size given by scaling the image.  The output is a PNG image.</p>
     */

    @Override
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

        Optional<PkgScreenshotImage> pkgScreenshotImageOptional = screenshot.tryGetPkgScreenshotImage();

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
     * @param ordering can be NULL; in which case the screenshot will come at the end.
     */

    @Override
    public PkgScreenshot storePkgScreenshotImage(
            InputStream input,
            ObjectContext context,
            Pkg pkg,
            Integer ordering) throws IOException, BadPkgScreenshotException {

        Preconditions.checkArgument(null != input, "the input must be provided");
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the package must be provided");

        byte[] pngData = ByteStreams.toByteArray(new BoundedInputStream(input, SCREENSHOT_SIZE_LIMIT));
        ImageHelper.Size size =  imageHelper.derivePngSize(pngData);
        String hashSha256 = HASH_FUNCTION.hashBytes(pngData).toString();

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

        // check that we do not already have this screenshot persisted for this package.

        for (PkgScreenshot pkgScreenshot : pkg.getPkgScreenshots()) {
            if (pkgScreenshot.getHashSha256().equals(hashSha256)) {
                LOGGER.warn("attempt to sure a screenshot image that is already stored for this package");
                throw new BadPkgScreenshotException();
            }
        }

        MediaType png = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get();

        // now we need to know the largest ordering so we can add this one at the end of the orderings
        // such that it is the next one in the list.

        int actualOrdering = null == ordering ? pkg.getHighestPkgScreenshotOrdering().orElse(0) + 1 : ordering;

        PkgScreenshot screenshot = context.newObject(PkgScreenshot.class);
        screenshot.setCode(UUID.randomUUID().toString());
        screenshot.setOrdering(actualOrdering);
        screenshot.setHeight(size.height);
        screenshot.setWidth(size.width);
        screenshot.setLength(pngData.length);
        screenshot.setHashSha256(hashSha256);
        pkg.addToManyTarget(Pkg.PKG_SCREENSHOTS.getName(), screenshot, true);

        PkgScreenshotImage screenshotImage = context.newObject(PkgScreenshotImage.class);
        screenshotImage.setMediaType(png);
        screenshotImage.setData(pngData);
        screenshot.addToManyTarget(PkgScreenshot.PKG_SCREENSHOT_IMAGES.getName(), screenshotImage, true);

        pkg.setModifyTimestamp();

        LOGGER.info("a screenshot #{} has been added to package [{}] ({})",
                actualOrdering, pkg.getName(), screenshot.getCode());

        for(Pkg subordinatePkg : pkgService.findSubordinatePkgsForMainPkg(context, pkg.getName())) {
            if (subordinatePkg.getName().endsWith(PkgService.SUFFIX_PKG_X86)) {
                storePkgScreenshotImage(
                        new ByteArrayInputStream(pngData),
                        context,
                        subordinatePkg,
                        actualOrdering);

                LOGGER.info("store of screenshot was extended [{}] -> [{}]", pkg.getName(),
                        subordinatePkg.getName());
            }
        }

        return screenshot;
    }

    @Override
    public void deleteScreenshot(
            ObjectContext context,
            PkgScreenshot screenshot) {

        Pkg pkg = screenshot.getPkg();
        String screenshotHashSha256 = screenshot.getHashSha256();

        screenshot.setPkg(null);

        Optional<PkgScreenshotImage> image = screenshot.tryGetPkgScreenshotImage();
        image.ifPresent(context::deleteObjects);
        context.deleteObjects(screenshot);

        for(Pkg subordinatePkg : pkgService.findSubordinatePkgsForMainPkg(context, pkg.getName())) {
            if (subordinatePkg.getName().endsWith(PkgService.SUFFIX_PKG_X86)) {
                List<PkgScreenshot> pkgScreenshotsToDelete = subordinatePkg.getPkgScreenshots()
                        .stream()
                        .filter(ps -> ps.getHashSha256().equals(screenshotHashSha256))
                        .collect(Collectors.toList());

                for (PkgScreenshot pkgScreenshotToDelete : pkgScreenshotsToDelete) {
                    deleteScreenshot(context, pkgScreenshotToDelete);
                }

                LOGGER.info("delete of screenshot was extended [{}] -> [{}]", pkg.getName(),
                        subordinatePkg.getName());
            }
        }
    }

    @Override
    public void replicatePkgScreenshots(
            ObjectContext context,
            Pkg sourcePkg,
            Pkg targetPkg) throws IOException, BadPkgScreenshotException {

        // remove any target screenshots which do not exist in the source.

        Set<String> existingSourceHashSha256s = sourcePkg
                .getPkgScreenshots()
                .stream()
                .map(_PkgScreenshot::getHashSha256)
                .collect(Collectors.toSet());

        targetPkg.getPkgScreenshots()
                .stream()
                .filter(ps -> !existingSourceHashSha256s.contains(ps.getHashSha256()))
                .forEach(ps -> deleteScreenshot(context, ps));

        // now add any screenshots that are not already in the target package.

        Set<String> existingTargetHashSha256s = targetPkg
                .getPkgScreenshots()
                .stream()
                .map(_PkgScreenshot::getHashSha256)
                .collect(Collectors.toSet());

        List<PkgScreenshot> sourcePkgScreenshotsToAddToTarget = sourcePkg.getPkgScreenshots()
                .stream()
                .filter(ps -> !existingTargetHashSha256s.contains(ps.getHashSha256()))
                .collect(Collectors.toList());

        for (PkgScreenshot ps : sourcePkgScreenshotsToAddToTarget) {
            storePkgScreenshotImage(
                    new ByteArrayInputStream(ps.getPkgScreenshotImage().getData()),
                    context,
                    targetPkg,
                    null);
        }

        // put the target screenshots into a correct ordering.

        Map<String, Integer> sourceOrderings = sourcePkg
                .getPkgScreenshots()
                .stream()
                .collect(Collectors.toMap(
                        _PkgScreenshot::getHashSha256,
                        _PkgScreenshot::getOrdering
                ));

        targetPkg.getPkgScreenshots().forEach(
                ps -> {
                    if (!sourceOrderings.containsKey(ps.getHashSha256())) {
                        throw new IllegalStateException("the target package is missing the screenshot with hash ["
                                + ps.getHashSha256() + "] for ordering.");
                    }

                    ps.setOrdering(sourceOrderings.get(ps.getHashSha256()));
                }
        );

    }

    @Override
    public void reorderPkgScreenshots(
            ObjectContext context,
            Pkg pkg,
            List<String> codes) {
        Preconditions.checkNotNull(codes);

        // first check that there are no duplicates.

        if(new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalArgumentException("the codes supplied contain duplicates which would interfere with the ordering");
        }

        // swap the codes for hashes because then the sorting can be replicated to
        // subordinate packages more easily.  This is because the hashes will be
        // the same, but the codes will differ.

        Map<String, String> codeToHashSha256Map = pkg.getPkgScreenshots()
                .stream()
                .collect(Collectors.toMap(_PkgScreenshot::getCode, _PkgScreenshot::getHashSha256));

        List<String> hashSha256s = codes.stream().map(codeToHashSha256Map::get).collect(Collectors.toList());

        reorderPkgScreenshotsByHashSha256s(pkg, hashSha256s);

        for(Pkg subordinatePkg : pkgService.findSubordinatePkgsForMainPkg(context, pkg.getName())) {
            if (subordinatePkg.getName().endsWith(PkgService.SUFFIX_PKG_X86)) {
                reorderPkgScreenshotsByHashSha256s(subordinatePkg, hashSha256s);
                LOGGER.info("reordering of screenshots was extended [{}] -> [{}]", pkg.getName(),
                        subordinatePkg.getName());
            }
        }
    }

    private void reorderPkgScreenshotsByHashSha256s(
            Pkg pkg,
            List<String> hashSha256s) {

        List<PkgScreenshot> screenshots = new ArrayList<>(pkg.getPkgScreenshots());

        screenshots.sort((o1, o2) -> {
            int o1i = hashSha256s.indexOf(o1.getHashSha256());
            int o2i = hashSha256s.indexOf(o2.getHashSha256());

            if (-1 == o1i && -1 == o2i) {
                return o1.getCode().compareTo(o2.getCode());
            }

            if (-1 == o1i) {
                o1i = Integer.MAX_VALUE;
            }

            if (-1 == o2i) {
                o2i = Integer.MAX_VALUE;
            }

            return Integer.compare(o1i, o2i);
        });

        for(int i=0;i<screenshots.size();i++) {
            PkgScreenshot pkgScreenshot = screenshots.get(i);
            pkgScreenshot.setOrdering(i+1);
        }
    }


}
