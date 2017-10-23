/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._LocalizationContent;
import org.haiku.haikudepotserver.support.cayenne.ExpressionCollector;

import java.util.*;

public class LocalizationContent extends _LocalizationContent {

    private static Optional<LocalizationContent> getInsertedLocalizationContent(
            ObjectContext context, String content) {

        return context.newObjects()
                .stream()
                .filter((no) -> LocalizationContent.class.isAssignableFrom(no.getClass()))
                .map(LocalizationContent.class::cast)
                .filter((lc) -> lc.getContent().equals(content))
                .findFirst();
    }

    public static Map<String, LocalizationContent> getOrCreateLocalizationContents(
            ObjectContext context, Collection<String> contents) {

        if (null == contents || contents.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, LocalizationContent> result = new HashMap<>();

        // first check the inserted objects to see if one already exists.

        contents
                .stream()
                .map((c) -> getInsertedLocalizationContent(context, c))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach((lc) -> result.put(lc.getContent(), lc));

        List<LocalizationContent> fetchedLocalizationContents = ObjectSelect
                .query(LocalizationContent.class)
                .where(contents
                        .stream()
                        .filter((c) -> !result.containsKey(c))
                        .map(CONTENT::eq)
                        .collect(ExpressionCollector.orExp()))
                .select(context);

        fetchedLocalizationContents.forEach((lc) -> result.put(lc.getContent(), lc));

        contents
                .stream()
                .filter((c) -> !result.containsKey(c))
                .map((c) -> {
                    LocalizationContent newLocalizationContent = context.newObject(LocalizationContent.class);
                            newLocalizationContent.setContent(c);
                            return newLocalizationContent;
                })
                .forEach((lc) -> result.put(lc.getContent(), lc));

        if (result.size() != contents.size()) {
            throw new IllegalStateException("mismatch between the requested contents and the resultant contents");
        }

        return result;
    }

}
