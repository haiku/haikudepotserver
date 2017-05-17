/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshotImage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

public interface PkgScreenshotService {


    /**
     * <p>This method will write the package's screenshot to the output stream.  It will constrain the output to the
     * size given by scaling the image.  The output is a PNG image.</p>
     */

    void writePkgScreenshotImage(
            OutputStream output,
            ObjectContext context,
            PkgScreenshot screenshot,
            int targetWidth,
            int targetHeight) throws IOException;
    /**
     * <p>This method will write the PNG data supplied in the input to the package as a screenshot.  Note that the icon
     * must comply with necessary characteristics.  If it is not compliant then an images of
     * {@link BadPkgScreenshotException} will be thrown.</p>
     */

    PkgScreenshot storePkgScreenshotImage(
            InputStream input,
            ObjectContext context,
            Pkg pkg,
            Integer ordering) throws IOException, BadPkgScreenshotException;

    void deleteScreenshot(ObjectContext context, PkgScreenshot screenshot);

}
