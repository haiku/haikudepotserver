/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._MediaType;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class MediaType extends _MediaType {

    public final static String MEDIATYPE_HAIKUVECTORICONFILE = "application/x-vnd.haiku-icon";
    public final static String MEDIATYPE_PNG = "image/png";

    public final static String EXTENSION_HAIKUVECTORICONFILE = "hvif";

    public final static String EXTENSION_PNG = "png";

    public static List<MediaType> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        return ObjectSelect.query(MediaType.class).orderBy(CODE.asc()).sharedCache().select(context);
    }

    /**
     * <p>Files can have extensions that help to signify what sort of files they are.  For example, a PNG file would
     * have the extension "png".  This method will be able to return a media type for a given file extension.</p>
     */

    public static Optional<MediaType> getByExtension(ObjectContext context, String extension) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(extension), "the extension must be provided");

        if(extension.equals(EXTENSION_HAIKUVECTORICONFILE)) {
            return tryGetByCode(context, MEDIATYPE_HAIKUVECTORICONFILE);
        }

        if(extension.equals(EXTENSION_PNG)) {
            return tryGetByCode(context, com.google.common.net.MediaType.PNG.toString());
        }

        return Optional.empty();
    }

    public static MediaType getByCode(ObjectContext context, final String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new IllegalStateException("unable to find media type by code [" + code + "]"));
    }

    public static Optional<MediaType> tryGetByCode(ObjectContext context, final String code) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be provided");
        return getAll(context).stream().filter(mt -> mt.getCode().equals(code)).collect(SingleCollector.optional());
    }

}
