/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface PkgScreenshotService {

    /**
     * @return true if the image was changed.
     */

    boolean optimizeScreenshot(ObjectContext context, PkgScreenshot screenshot)
            throws IOException, BadPkgScreenshotException;

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
            PkgSupplement pkg,
            Integer ordering) throws IOException, BadPkgScreenshotException;

    void deleteScreenshot(ObjectContext context, PkgScreenshot screenshot);

    /**
     * <p>This method will re-order the screenshots according to the set of codes present in the supplied list.
     * If the same code appears twice in the list, an {@link java.lang.IllegalArgumentException} will be
     * thrown.  Any screenshots that are not mentioned in the list will be indeterminately ordered at the end
     * of the list.</p>
     */

    void reorderPkgScreenshots(
            ObjectContext context,
            PkgSupplement pkg,
            List<String> codes);

}
