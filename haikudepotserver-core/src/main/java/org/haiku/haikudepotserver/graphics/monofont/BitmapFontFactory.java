/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.monofont;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BitmapFontFactory {

    private static final Pattern CHARACTER_REF = Pattern.compile("^0x([0-9a-fA-F]{4})$");

    /**
     * <p>This method will parse a bitmap font into an object representation
     * from a stream of text data.</p>
     */

    public static BitmapFont parseFont(InputStream inputStream) throws IOException {
        Preconditions.checkArgument(null != inputStream, "the input stream must be provided");
        try (
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            return parseFont(bufferedReader);
        }
    }

    private static BitmapFont parseFont(BufferedReader bufferedReader) throws IOException {
        Map<Character, BitmapCharacter> characters = new HashMap<>();

        String line;

        while(null != (line = bufferedReader.readLine())) {
            line = StringUtils.trimToNull(line);

            if (null != line && !line.startsWith("#")) {
                Matcher matcher = CHARACTER_REF.matcher(line);
                if (!matcher.matches()) {
                    throw new IOException("unable to parse character reference from line [" + line + "]");
                }
                characters.put(
                        (char) Integer.parseInt(matcher.group(1), 16),
                        parseCharacter(bufferedReader));
            }
        }
        return new BitmapFont(characters);
    }

    private static BitmapCharacter parseCharacter(BufferedReader bufferedReader) throws IOException {
        String line;
        int height = 0;
        int width = 0;
        BitSet bitSet = new BitSet();

        while (null != (line = StringUtils.trimToNull(bufferedReader.readLine()))) {
            if (0 == width) {
                width = line.length();
            }
            else {
                if (width != line.length()) {
                    throw new IllegalStateException("row width mismatch");
                }
            }
            for (int i = 0; i < line.length(); i++) {
                switch (line.charAt(i)) {
                    case '*' -> bitSet.set(height * width + i);
                    case '.' -> bitSet.clear(height * width + i);
                    default -> throw new IllegalStateException("malformed row [" + line + "]");
                }
            }
            height++;
        }

        return new BitmapCharacter(bitSet, width, height);
    }

}
