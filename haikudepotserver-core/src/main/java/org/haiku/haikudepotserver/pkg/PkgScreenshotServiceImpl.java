/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.ObjectContext;
import org.apache.commons.io.input.BoundedInputStream;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshotImage;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.haiku.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotService;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

@Service
public class PkgScreenshotServiceImpl implements PkgScreenshotService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgIconServiceImpl.class);

    private ImageHelper imageHelper = new ImageHelper();

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

        int actualOrdering = null == ordering ? pkg.getHighestPkgScreenshotOrdering().orElse(0) + 1 : ordering;

        PkgScreenshot screenshot = context.newObject(PkgScreenshot.class);
        screenshot.setCode(UUID.randomUUID().toString());
        screenshot.setOrdering(actualOrdering);
        screenshot.setHeight(size.height);
        screenshot.setWidth(size.width);
        screenshot.setLength(pngData.length);
        pkg.addToManyTarget(Pkg.PKG_SCREENSHOTS_PROPERTY, screenshot, true);

        PkgScreenshotImage screenshotImage = context.newObject(PkgScreenshotImage.class);
        screenshotImage.setMediaType(png);
        screenshotImage.setData(pngData);
        screenshot.addToManyTarget(PkgScreenshot.PKG_SCREENSHOT_IMAGES_PROPERTY, screenshotImage, true);

        pkg.setModifyTimestamp(new java.util.Date());

        LOGGER.info("a screenshot #{} has been added to package {} ({})",
                actualOrdering, pkg.getName(), screenshot.getCode());

        return screenshot;
    }

    @Override
    public void deleteScreenshot(
            ObjectContext context,
            PkgScreenshot screenshot) {

        screenshot.setPkg(null);
        Optional<PkgScreenshotImage> image = screenshot.tryGetPkgScreenshotImage();
        image.ifPresent(context::deleteObjects);
        context.deleteObjects(screenshot);
    }


}
