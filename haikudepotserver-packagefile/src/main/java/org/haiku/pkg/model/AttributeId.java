/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

/**
 * <p>These constants define the meaning of an {@link Attribute}.  The numerical value is a value that comes up
 * in the formatted file that maps to these constants.  The string value is a name for the attribute and the type
 * gives the type that is expected to be associated with an {@link Attribute} that has one of these IDs.</p>
 *
 * <p>These constants were obtained from
 * <a href="http://cgit.haiku-os.org/haiku/tree/headers/os/package/hpkg/PackageAttributes.h">here</a> and then the
 * search/replace of <code>B_DEFINE_HPKG_ATTRIBUTE\([ ]*(\d+),[ ]*([A-Z]+),[ \t]*("[a-z:\-.]+"),[ \t]*([A-Z_]+)\)</code>
 * / <code>$4($1,$3,$2),</code> was applied.</p>
 */

public enum AttributeId {

    DIRECTORY_ENTRY(0, "dir:entry", AttributeType.STRING),
    FILE_TYPE(1, "file:type", AttributeType.INT),
    FILE_PERMISSIONS(2, "file:permissions", AttributeType.INT),
    FILE_USER(3, "file:user", AttributeType.STRING),
    FILE_GROUP(4, "file:group", AttributeType.STRING),
    FILE_ATIME(5, "file:atime", AttributeType.INT),
    FILE_MTIME(6, "file:mtime", AttributeType.INT),
    FILE_CRTIME(7, "file:crtime", AttributeType.INT),
    FILE_ATIME_NANOS(8, "file:atime:nanos", AttributeType.INT),
    FILE_MTIME_NANOS(9, "file:mtime:nanos", AttributeType.INT),
    FILE_CRTIM_NANOS(10, "file:crtime:nanos", AttributeType.INT),
    FILE_ATTRIBUTE(11, "file:attribute", AttributeType.STRING),
    FILE_ATTRIBUTE_TYPE(12, "file:attribute:type", AttributeType.INT),
    DATA(13, "data", AttributeType.RAW),
    SYMLINK_PATH(14, "symlink:path", AttributeType.STRING),
    PACKAGE_NAME(15, "package:name", AttributeType.STRING),
    PACKAGE_SUMMARY(16, "package:summary", AttributeType.STRING),
    PACKAGE_DESCRIPTION(17, "package:description", AttributeType.STRING),
    PACKAGE_VENDOR(18, "package:vendor", AttributeType.STRING),
    PACKAGE_PACKAGER(19, "package:packager", AttributeType.STRING),
    PACKAGE_FLAGS(20, "package:flags", AttributeType.INT),
    PACKAGE_ARCHITECTURE(21, "package:architecture", AttributeType.INT),
    PACKAGE_VERSION_MAJOR(22, "package:version.major", AttributeType.STRING),
    PACKAGE_VERSION_MINOR(23, "package:version.minor", AttributeType.STRING),
    PACKAGE_VERSION_MICRO(24, "package:version.micro", AttributeType.STRING),
    PACKAGE_VERSION_REVISION(25, "package:version.revision", AttributeType.INT),
    PACKAGE_COPYRIGHT(26, "package:copyright", AttributeType.STRING),
    PACKAGE_LICENSE(27, "package:license", AttributeType.STRING),
    PACKAGE_PROVIDES(28, "package:provides", AttributeType.STRING),
    PACKAGE_REQUIRES(29, "package:requires", AttributeType.STRING),
    PACKAGE_SUPPLEMENTS(30, "package:supplements", AttributeType.STRING),
    PACKAGE_CONFLICTS(31, "package:conflicts", AttributeType.STRING),
    PACKAGE_FRESHENS(32, "package:freshens", AttributeType.STRING),
    PACKAGE_REPLACES(33, "package:replaces", AttributeType.STRING),
    PACKAGE_RESOLVABLE_OPERATOR(34, "package:resolvable.operator", AttributeType.INT),
    PACKAGE_CHECKSUM(35, "package:checksum", AttributeType.STRING),
    PACKAGE_VERSION_PRE_RELEASE(36, "package:version.prerelease", AttributeType.STRING),
    PACKAGE_PROVIDES_COMPATIBLE(37, "package:provides.compatible", AttributeType.STRING),
    PACKAGE_URL(38, "package:url", AttributeType.STRING),
    PACKAGE_SOURCE_URL(39, "package:source-url", AttributeType.STRING),
    PACKAGE_INSTALL_PATH(40, "package:install-path", AttributeType.STRING),
    PACKAGE_BASE_PACKAGE(41, "package:base-package", AttributeType.STRING),
    PACKAGE_GLOBAL_WRITABLE_FILE(42, "package:global-writable-file", AttributeType.STRING),
    PACKAGE_USER_SETTINGS_FILE(43, "package:user-settings-file", AttributeType.STRING),
    PACKAGE_WRITABLE_FILE_UPDATE_TYPE(44, "package:writable-file-update-type", AttributeType.INT),
    PACKAGE_SETTINGS_FILE_TEMPLATE(45, "package:settings-file-template", AttributeType.STRING),
    PACKAGE_USER(46, "package:user", AttributeType.STRING),
    PACKAGE_USER_REAL_NAME(47, "package:user.real-name", AttributeType.STRING),
    PACKAGE_USER_HOME(48, "package:user.home", AttributeType.STRING),
    PACKAGE_USER_SHELL(49, "package:user.shell", AttributeType.STRING),
    PACKAGE_USER_GROUP(50, "package:user.group", AttributeType.STRING),
    PACKAGE_GROUP(51, "package:group", AttributeType.STRING),
    PACKAGE_POST_INSTALL_SCRIPT(52, "package:post-install-script", AttributeType.STRING),
    PACKAGE_IS_WRITABLE_DIRECTORY(53, "package:is-writable-directory", AttributeType.INT),
    PACKAGE(54, "package", AttributeType.STRING);

    private final int code;
    private final String name;
    private final AttributeType attributeType;

    AttributeId(int code, String name, AttributeType attributeType) {
        this.code = code;
        this.name = name;
        this.attributeType = attributeType;
    }

    int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AttributeType getAttributeType() {
        return attributeType;
    }

}
