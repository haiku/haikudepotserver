/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface PkgIconService {

    /**
     * <p>Removes all icons that are stored on this package.</p>
     */

    void removePkgIcon(ObjectContext context, Pkg pkg);

    /**
     * <p>This method will write the icon data supplied in the input to the package as its icon.  Note that the icon
     * must comply with necessary characteristics; for example it must be either 16 or 32 pixels along both its sides
     * if it is a PNG.  If it is non-compliant then an instance of
     * {@link BadPkgIconException} will be thrown.</p>
     *
     * <p>This method will also use apply PNG optimization if this is possible.</p>
     */

    PkgIcon storePkgIconImage(
            InputStream input,
            MediaType mediaType,
            Integer expectedSize,
            ObjectContext context,
            Pkg pkg) throws IOException, BadPkgIconException;

    /**
     * <p>This method will transfer the data from the nominated pkg icon over into the target package.</p>
     */

    PkgIcon replicatePkgIcon(
            ObjectContext context,
            PkgIcon pkgIcon,
            Pkg targetPkg) throws IOException, BadPkgIconException;

    void replicatePkgIcons(
            ObjectContext context,
            Pkg sourcePkg,
            Pkg targetPkg) throws IOException, BadPkgIconException;

    /**
     * <p>The packages are configured with icons.  Each icon has a media type and,
     * optionally a size.  This method will return all of those possible media
     * type + size combinations that are actually in use at the moment.  The list
     * will be unique.</p>
     */

    List<PkgIconConfiguration> getInUsePkgIconConfigurations(ObjectContext objectContext);

}
