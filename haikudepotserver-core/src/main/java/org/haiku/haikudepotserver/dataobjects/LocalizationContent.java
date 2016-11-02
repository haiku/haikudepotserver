/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haiku.haikudepotserver.dataobjects.auto._LocalizationContent;

import java.util.List;

public class LocalizationContent extends _LocalizationContent {

    public static LocalizationContent getOrCreateLocalizationContent(ObjectContext context, String content) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(content), "localization content must be supplied");

        // first check the inserted objects to see if one already exists.

        for(Object newObject : context.newObjects()) {
            if(LocalizationContent.class.isAssignableFrom(newObject.getClass())) {
                LocalizationContent newLocalizationContent = (LocalizationContent) newObject;

                if(newLocalizationContent.getContent().equals(content)) {
                    return newLocalizationContent;
                }
            }
        }

        SelectQuery query = new SelectQuery(
                LocalizationContent.class,
                ExpressionFactory.matchExp(LocalizationContent.CONTENT_PROPERTY, content));

        List<LocalizationContent> results = context.performQuery(query);

        switch(results.size()) {
            case 0:
                LocalizationContent result = context.newObject(LocalizationContent.class);
                result.setContent(content);
                return result;

            case 1:
                return results.get(0);

            default:
                throw new IllegalStateException("found " + results.size() + " entries in the localization content for content");

        }

    }

}
