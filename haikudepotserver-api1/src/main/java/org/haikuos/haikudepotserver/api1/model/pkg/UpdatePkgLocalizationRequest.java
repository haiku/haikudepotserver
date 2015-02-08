/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class UpdatePkgLocalizationRequest {

    public String pkgName;

    public List<PkgLocalization> pkgLocalizations;

}
