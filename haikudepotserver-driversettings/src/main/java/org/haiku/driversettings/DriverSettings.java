/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.driversettings;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class DriverSettings {

    private final static char CHAR_LINE_SEPARATOR = ';';

    public static List<Parameter> parse(Reader reader)
            throws DriverSettingsException, IOException {
        return parse(reader, false);
    }

    private static void skipToStartOfNextLine(Reader reader) throws IOException {
        while (true) {
            reader.mark(1);
            int c = reader.read();

            if (-1 == c || '\n' == c) {
                return;
            }
        }
    }

    private static void skipSpaceAndComments(Reader reader) throws IOException {
        int c;

        do {
            reader.mark(1);
            c = reader.read();

            if ('#' == c) {
                skipToStartOfNextLine(reader);
            }
        } while (Character.isSpaceChar(c) || c == '\t');

        reader.reset();
    }

    private static Token nextToken(Reader reader) throws DriverSettingsException, IOException {

        skipSpaceAndComments(reader);

        boolean first = true;
        boolean escape = false;
        boolean quoted = false;
        StringBuilder result = new StringBuilder();

        while (true) {
            reader.mark(1);
            int c = reader.read();

            if (first) {
                switch (c) {
                    case -1 -> {
                        return new Token(TokenType.EOF);
                    }
                    case CHAR_LINE_SEPARATOR, '\n' -> {
                        return new Token(TokenType.SEPARATOR);
                    }
                    case '}' -> {
                        return new Token(TokenType.CLOSEPARAMETERS);
                    }
                    case '{' -> {
                        return new Token(TokenType.OPENPARAMETERS);
                    }
                }
            }

            if (quoted && !escape && c == '"') {
                return new Token(result.toString());
            }

            if (!quoted && (Character.isWhitespace(c) || (!escape && c == CHAR_LINE_SEPARATOR))) {
                reader.reset();
                return new Token(result.toString());
            }

            if (-1 == c || '\n' == c) {
                if (escape) {
                    throw new DriverSettingsException("end of file / line reached but was expecting an escaped character");
                }

                if (quoted) {
                    throw new DriverSettingsException("end of file / line reached but was expecting a quoted character");
                }

                reader.reset();
                return new Token(result.toString());
            } else {
                if (escape) {
                    result.append((char) c);
                } else {
                    switch (c) {
                        case '"' -> {
                            if (first) {
                                quoted = true;
                            } else {
                                result.append((char) c);
                            }
                        }
                        case '\\' -> escape = true;
                        default -> result.append((char) c);
                    }
                }
            }

            first = false;
        }
    }

    private static List<Parameter> parse(Reader reader, boolean withOpenParameters)
            throws DriverSettingsException, IOException {

        ParameterBuilder parameterBuilder = ParameterBuilder.newBuilder();
        List<Parameter> result = new ArrayList<>();

        while (true) {
            Token token = nextToken(reader);

            switch (token.getTokenType()) {
                case CLOSEPARAMETERS -> {
                    if (!withOpenParameters) {
                        throw new DriverSettingsException("dangling close parameters");
                    }
                }
                case EOF -> {
                    if (withOpenParameters) {
                        throw new DriverSettingsException("unexpected end of file; expecting '}' to terminate parameters");
                    }
                }
            }

            switch (token.getTokenType()) {
                case CLOSEPARAMETERS, EOF, SEPARATOR -> {
                    if (parameterBuilder.hasName()) {
                        result.add(parameterBuilder.build());
                        parameterBuilder = ParameterBuilder.newBuilder();
                    }
                }
            }

            switch (token.getTokenType()) {
                case EOF, CLOSEPARAMETERS -> {
                    return result;
                }
                case OPENPARAMETERS -> {
                    if (!parameterBuilder.hasName()) {
                        throw new DriverSettingsException("starting new block of parameters without a name");
                    }
                    parameterBuilder.withParameters(parse(reader, true));
                    // there can be no more data after the parameters.
                    result.add(parameterBuilder.build());
                    parameterBuilder = ParameterBuilder.newBuilder();
                }
                case WORD -> parameterBuilder.withWord(token.getContent());
            }
        }
    }

    private enum TokenType {
        SEPARATOR,
        EOF,
        WORD,
        OPENPARAMETERS,
        CLOSEPARAMETERS
    }

    private static class Token {
        private final TokenType tokenType;
        private final String content;

        Token(String content) {
            Preconditions.checkArgument(null != content && 0 != content.length(), "bad token; zero length");
            this.tokenType = TokenType.WORD;
            this.content = content;
        }

        Token(TokenType tokenType) {
            this.tokenType = tokenType;
            this.content = null;
        }

        TokenType getTokenType() {
            return tokenType;
        }

        String getContent() {
            return content;
        }
    }


}
