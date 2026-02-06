/*
 * Copyright 2020-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;

import java.security.MessageDigest;
import java.util.List;

public class PasswordEncoder implements org.springframework.security.crypto.password.PasswordEncoder {

    private final BytesKeyGenerator saltGenerator;

    public PasswordEncoder(BytesKeyGenerator saltGenerator) {
        this.saltGenerator = Preconditions.checkNotNull(saltGenerator);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        Preconditions.checkArgument(StringUtils.isNotBlank(rawPassword));
        return encode(saltGenerator.generateKey(), rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        List<String> saltAndHash = Splitter.on(".").splitToList(encodedPassword);
        if (2 != saltAndHash.size()) {
            throw new IllegalStateException("bad encoded password");
        }
        byte[] saltBytes = BaseEncoding.base16().decode(saltAndHash.getFirst().toUpperCase());
        return Strings.CS.equals(encodedPassword, encode(saltBytes, rawPassword));
    }

    private String encode(byte[] saltBytes, CharSequence rawPassword) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(Utf8.encode(rawPassword));
            sha.update(saltBytes);
            return String.join(".",
                    BaseEncoding.base16().encode(saltBytes).toLowerCase(),
                    BaseEncoding.base16().encode(sha.digest()).toLowerCase());
        }
        catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("no SHA-256 crypt algorithm available",e);
        }
    }

}
