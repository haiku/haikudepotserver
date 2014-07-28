/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import org.haikuos.haikudepotserver.dataobjects.auto._PkgVersionLocalization;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

public class PkgVersionLocalization extends _PkgVersionLocalization implements CreateAndModifyTimestamped {

    public boolean equalsForContent(PkgVersionLocalization other) {
        return
                getSummary().equals(other.getSummary())
                && getDescription().equals(other.getDescription());
    }

}
