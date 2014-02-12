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
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgScreenshot;

import java.util.List;

public class PkgScreenshot extends _PkgScreenshot implements Comparable<PkgScreenshot> {

    public static Optional<PkgScreenshot> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<PkgScreenshot>) context.performQuery(new SelectQuery(
                        PkgScreenshot.class,
                        ExpressionFactory.matchExp(PkgScreenshot.CODE_PROPERTY, code))),
                null));
    }

    /**
     * <p>As there should be only one of these, if there are two then this method will throw an
     * {@link IllegalStateException}.</p>
     */

    public Optional<PkgScreenshotImage> getPkgScreenshotImage() {
        List<PkgScreenshotImage> images = getPkgScreenshotImages();

        switch(images.size()) {
            case 0: return Optional.absent();
            case 1: return Optional.of(images.get(0));
            default:
                throw new IllegalStateException("more than one pkg icon image found on an icon image");
        }
    }

    @Override
    public int compareTo(PkgScreenshot o) {
        return getOrdering().compareTo(o.getOrdering());
    }
}
