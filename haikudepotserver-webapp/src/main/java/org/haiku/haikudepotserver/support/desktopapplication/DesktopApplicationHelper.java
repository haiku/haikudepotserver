/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.desktopapplication;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>Support functions for the desktop application.</p>
 */
public class DesktopApplicationHelper {

    private static final String USER_AGENT_PREFIX_HAIKU_DEPOT = "HaikuDepot/";

    private static final Pattern PATTERN_VERSION_COMPONENT = Pattern.compile("^(0|[1-9][0-9]*)$");

    public static boolean matchesUserAgent(String userAgent) {
        return StringUtils.trimToEmpty(userAgent).startsWith(USER_AGENT_PREFIX_HAIKU_DEPOT);
    }

    public static Optional<int[]> tryDeriveVersionFromUserAgent(String userAgent) {
        return Optional.ofNullable(StringUtils.trimToNull(userAgent))
                .filter(ua -> ua.startsWith(USER_AGENT_PREFIX_HAIKU_DEPOT))
                .map(ua -> StringUtils.removeStart(ua, USER_AGENT_PREFIX_HAIKU_DEPOT))
                .flatMap(DesktopApplicationHelper::tryDeriveVersion);
    }

    public static int[] deriveVersion(String versionString) {
        return tryDeriveVersion(versionString)
                .orElseThrow(() -> new RuntimeException("unable to derive version from [" + versionString + "]"));
    }

    public static Optional<int[]> tryDeriveVersion(String versionString) {
        return Optional.ofNullable(versionString)
                .map(versionStr -> Splitter.on(".").splitToList(versionStr))
                .filter(versionComponents -> !versionComponents.isEmpty())
                .filter(versionComponents -> versionComponents.stream().allMatch(c -> PATTERN_VERSION_COMPONENT.matcher(c).matches()))
                .map(versionComponents -> versionComponents.stream().mapToInt(Integer::parseInt).toArray());
    }

    public static String versionToString(int[] version) {
        Preconditions.checkArgument(null != version, "the version is required");
        Preconditions.checkArgument(0 != version.length, "the version is not able to be empty");
        return Arrays.stream(version).mapToObj(Integer::toString).collect(Collectors.joining("."));
    }

}
