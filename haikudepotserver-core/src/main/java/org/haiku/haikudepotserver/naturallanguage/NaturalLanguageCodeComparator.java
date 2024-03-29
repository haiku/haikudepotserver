/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.naturallanguage;

import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;

import java.util.Comparator;

/**
 * <p>This comparator will provide a means of sorting the {@link NaturalLanguage} objects
 * and will consider the possibility of fields being <code>null</code>. The sort order is
 * entirely based on the codes rather than the name of the language.</p>
 */

public class NaturalLanguageCodeComparator implements Comparator<NaturalLanguageCoded> {

    @Override
    public int compare(NaturalLanguageCoded nl1, NaturalLanguageCoded nl2) {
        int result = nl1.getLanguageCode().compareTo(nl2.getLanguageCode());

        if (0 != result) {
            return result;
        }

        result = StringUtils.compare(nl1.getCountryCode(), nl2.getCountryCode(), false);

        if (0 != result) {
            return result;
        }

        return StringUtils.compare(nl1.getScriptCode(), nl2.getScriptCode(), false);
    }

}
