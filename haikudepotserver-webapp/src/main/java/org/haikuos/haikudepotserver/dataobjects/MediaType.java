/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.auto._MediaType;

import java.util.List;

public class MediaType extends _MediaType {

    public final static String MEDIATYPE_HAIKUVECTORICONFILE = "application/x-vnd.haiku-icon";

    public final static String EXTENSION_HAIKUVECTORICONFILE = "hvif";

    public final static String EXTENSION_PNG = "png";

    public static List<MediaType> getAll(ObjectContext context) {
        Preconditions.checkNotNull(context);
        SelectQuery query = new SelectQuery(MediaType.class);
        query.addOrdering(new Ordering(CODE_PROPERTY, SortOrder.ASCENDING));
        return (List<MediaType>) context.performQuery(query);
    }

    /**
     * <p>Files can have extensions that help to signify what sort of files they are.  For example, a PNG file would
     * have the extension "png".  This method will be able to return a media type for a given file extension.</p>
     */

    public static Optional<MediaType> getByExtension(ObjectContext context, String extension) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(extension));

        if(extension.equals(EXTENSION_HAIKUVECTORICONFILE)) {
            return getByCode(context, MEDIATYPE_HAIKUVECTORICONFILE);
        }

        if(extension.equals(EXTENSION_PNG)) {
            return getByCode(context, com.google.common.net.MediaType.PNG.toString());
        }

        return Optional.absent();
    }

    public static Optional<MediaType> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<MediaType>) context.performQuery(new SelectQuery(
                        MediaType.class,
                        ExpressionFactory.matchExp(MediaType.CODE_PROPERTY, code))),
                null));
    }

}
