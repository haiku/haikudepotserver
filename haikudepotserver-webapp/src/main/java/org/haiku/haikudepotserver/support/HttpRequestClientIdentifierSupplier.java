/*
 * Copyright 2016-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.net.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class HttpRequestClientIdentifierSupplier implements ClientIdentifierSupplier {

    private static final Pattern PATTERN_IPV4_ADDRESS = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)$");

    private static final HashFunction HASH = Hashing.sha256();

    @Autowired(required = false)
    private HttpServletRequest request;

    @Override
    public Optional<String> get() {
        return Stream.of(
                Optional.ofNullable(request)
                        .map(r -> r.getHeader(HttpHeaders.X_FORWARDED_FOR))
                        .map(StringUtils::trimToNull),
                Optional.ofNullable(request)
                        .map(ServletRequest::getRemoteAddr)
        )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(HttpRequestClientIdentifierSupplier::toObfuscated)
                .findFirst();
    }

    @VisibleForTesting
    static String toObfuscated(String str) {
        Matcher ipv4Matcher = PATTERN_IPV4_ADDRESS.matcher(str);

        if (ipv4Matcher.matches()) {
            return HASH.hashString(str, Charsets.UTF_8) + "." + ipv4Matcher.group(4);
        }

        return HASH.hashString(str, Charsets.UTF_8).toString();
    }

}

