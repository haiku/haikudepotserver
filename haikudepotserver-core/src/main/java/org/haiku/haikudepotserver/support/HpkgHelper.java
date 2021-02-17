/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import org.haiku.pkg.AttributeContext;
import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.AttributeId;
import org.haiku.pkg.model.FileAttributesValues;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HpkgHelper {

    /**
     * <p>This will find all of the directory entries.  In these it will find all of the
     * directory entries that are executable and then it will find those that have an icon.  It will return
     * a list of those attributes that are the icons.</p>
     */

    public static List<Attribute> findIconAttributesFromExecutableDirEntries(
            AttributeContext context,
            List<Attribute> toc) {
        return streamDirEntries(toc)
                .flatMap(appA -> streamExecutableDirEntriesRecursively(context, appA))
                .flatMap(execA -> streamIconAttributesFromAppExecutableDirEntry(context, execA))
                .collect(Collectors.toList());
    }

    private static Stream<Attribute> streamDirEntries(List<Attribute> toc) {
        return toc.stream().filter(a -> a.getAttributeId() == AttributeId.DIRECTORY_ENTRY);
    }

    private static Stream<Attribute> streamExecutableDirEntriesRecursively(
            AttributeContext context,
            Attribute attribute) {
        if (attribute.getAttributeId() != AttributeId.DIRECTORY_ENTRY) {
            return Stream.of();
        }

        if (isExecutableDirEntry(context, attribute)) {
            return Stream.of(attribute);
        }

        return attribute.getChildAttributes()
                .stream()
                .flatMap(a -> streamExecutableDirEntriesRecursively(context, a));
    }

    private static boolean isExecutableDirEntry(
            AttributeContext context,
            Attribute attr) {
        if (attr.getAttributeId() != AttributeId.DIRECTORY_ENTRY) {
            return false;
        }

        return attr.tryGetChildAttribute(AttributeId.FILE_PERMISSIONS)
                .map(a -> ((Number) a.getValue(context)).intValue())
                .filter(permissions -> 0 != (0100 & permissions))
                // ^^ posix permissions checking to see that the user has the executable flag set
                .isPresent();
    }

    private static Stream<Attribute> streamIconAttributesFromAppExecutableDirEntry(
            AttributeContext context,
            Attribute executableDirEntryAttribute) {
        String desiredAttributeValue = FileAttributesValues.BEOS_ICON.getAttributeValue();
        return executableDirEntryAttribute.getChildAttributes(AttributeId.FILE_ATTRIBUTE)
                .stream()
                .filter(a -> a.getValue(context).toString().equals(desiredAttributeValue));
    }

}
