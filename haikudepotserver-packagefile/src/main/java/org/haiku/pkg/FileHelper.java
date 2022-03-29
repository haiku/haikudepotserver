/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import org.haiku.pkg.model.FileType;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/**
 * <p>This helps out with typical common reads that might be performed as part of
 * parsing various values in the HPKR file.</p>
 */

public class FileHelper {

    private final static BigInteger MAX_BIGINTEGER_FILE = new BigInteger(Long.toString(Long.MAX_VALUE));

    private final byte[] buffer8 = new byte[8];

    public FileType getType(RandomAccessFile file) throws IOException {
        return tryGetType(file).orElseThrow(() -> new HpkException("unable to establish the file type"));
    }

    public Optional<FileType> tryGetType(RandomAccessFile file) throws IOException {
        String magicString = new String(readMagic(file));
        return EnumSet.allOf(FileType.class).stream()
                .filter(e -> e.name().toLowerCase(Locale.ROOT).equals(magicString))
                .findFirst();
    }

    public int readUnsignedShortToInt(RandomAccessFile randomAccessFile) throws IOException {

        if (2 != randomAccessFile.read(buffer8, 0, 2)) {
            throw new HpkException("not enough bytes read for an unsigned short");
        }

        int i0 = buffer8[0] & 0xff;
        int i1 = buffer8[1] & 0xff;

        return i0 << 8 | i1;
    }

    public long readUnsignedIntToLong(RandomAccessFile randomAccessFile) throws IOException {

        if (4 != randomAccessFile.read(buffer8, 0, 4)) {
            throw new HpkException("not enough bytes read for an unsigned int");
        }

        long l0 = buffer8[0] & 0xff;
        long l1 = buffer8[1] & 0xff;
        long l2 = buffer8[2] & 0xff;
        long l3 = buffer8[3] & 0xff;

        return l0 << 24 | l1 << 16 | l2 << 8 | l3;
    }

    public BigInteger readUnsignedLong(RandomAccessFile randomAccessFile) throws IOException {

        if (8 != randomAccessFile.read(buffer8)) {
            throw new HpkException("not enough bytes read for an unsigned long");
        }

        return new BigInteger(1, buffer8);
    }

    public long readUnsignedLongToLong(RandomAccessFile randomAccessFile) throws IOException {

        BigInteger result = readUnsignedLong(randomAccessFile);

        if (result.compareTo(MAX_BIGINTEGER_FILE) > 0) {
            throw new HpkException("the hpkr file contains an unsigned long which is larger than can be represented in a java long");
        }

        return result.longValue();
    }

    public char[] readMagic(RandomAccessFile randomAccessFile) throws IOException {

        if (4 != randomAccessFile.read(buffer8, 0, 4)) {
            throw new HpkException("not enough bytes read for a 4-byte magic");
        }

        return new char[] {
                (char) buffer8[0],
                (char) buffer8[1],
                (char) buffer8[2],
                (char) buffer8[3]
        };
    }


}
