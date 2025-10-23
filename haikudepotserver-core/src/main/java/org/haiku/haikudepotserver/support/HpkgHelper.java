/*
 * Copyright 2021-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import org.apache.commons.collections4.CollectionUtils;
import org.haiku.pkg.AttributeContext;
import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.AttributeId;
import org.haiku.pkg.model.FileAttributesValues;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HpkgHelper {

    /**
     * <p>Returns true if the TOC contains a deskbar link which would indicate that it is a desktop
     * application.</p>
     */

    public static boolean hasDesktopLink(
            AttributeContext context,
            List<Attribute> toc) {
        Preconditions.checkArgument(null != context, "context may not be null");
        Preconditions.checkArgument(null != toc, "toc may not be null");
        Optional<Attribute> applicationsOptional = tryFindDirAtPath(
                context,
                toc,
                List.of("data", "deskbar", "menu", "Applications"));

        return applicationsOptional
                .filter(a -> a.getChildAttributes().stream().anyMatch(HpkgHelper::hasSymLinkDirectoryEntry))
                .isPresent();
    }

    /**
     * <p>This will find all the directory entries.  In these it will find all the
     * directory entries that are executable then it will find those that have an icon. It will return
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

    /**
     * <p>Tries to find the {@link Attribute} at the file-path supplied.</p>
     */

    private static Optional<Attribute> tryFindDirAtPath(
            AttributeContext context,
            List<Attribute> roots,
            List<String> path) {
        Preconditions.checkArgument(CollectionUtils.isNotEmpty(path));

        String path0 = path.getFirst();

        Optional<Attribute> root0Optional = roots.stream()
                .filter(a -> a.getAttributeId() == AttributeId.DIRECTORY_ENTRY)
                .filter(a -> a.getValue(context).equals(path0))
                .findFirst();

        if (1 == path.size()) {
            return root0Optional;
        }

        if (root0Optional.isEmpty()) {
            return Optional.empty();
        }

        Attribute root0 = root0Optional.get();
        return tryFindDirAtPath(context, root0.getChildAttributes(), path.subList(1, path.size()));
    }

    /**
     * @return true if this attribute is a directory entry and is a symlink.
     */

    private static boolean hasSymLinkDirectoryEntry(Attribute attr) {
        if (attr.getAttributeId() != AttributeId.DIRECTORY_ENTRY) {
            return false;
        }

        return attr.getChildAttributes().stream()
                .anyMatch(a -> a.getAttributeId() == AttributeId.SYMLINK_PATH);
    }

}
