/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class GetPkgLocalizationsRequest {

    public String pkgName;

    public List<String> naturalLanguageCodes;

}
