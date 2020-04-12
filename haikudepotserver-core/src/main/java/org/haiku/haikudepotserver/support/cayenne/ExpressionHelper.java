/*
 * Copyright 2018-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.support.VersionCoordinates;

import java.util.Collection;

public class ExpressionHelper {

    public static Expression toExpression(VersionCoordinates coordinates) {
        return toExpression(coordinates, null);
    }

    private static String prefixKey(String prefix, String key) {
        if(null==prefix) {
            return key;
        }

        return prefix + "." + key;
    }

    /**
     * <p>This method will produce an expression that can be used in a Cayenne query.</p>
     */

    public static Expression toExpression(VersionCoordinates coordinates, String prefix) {
        Preconditions.checkNotNull(coordinates);
        return ExpressionFactory.and(
            ExpressionFactory.matchExp(prefixKey(prefix, PkgVersion.MAJOR.getName()), coordinates.getMajor()),
            ExpressionFactory.matchExp(prefixKey(prefix, PkgVersion.MINOR.getName()), coordinates.getMinor()),
            ExpressionFactory.matchExp(prefixKey(prefix, PkgVersion.MICRO.getName()), coordinates.getMicro()),
            ExpressionFactory.matchExp(prefixKey(prefix, PkgVersion.PRE_RELEASE.getName()), coordinates.getPreRelease()),
            ExpressionFactory.matchExp(prefixKey(prefix, PkgVersion.REVISION.getName()), coordinates.getRevision())
        );
    }

}
