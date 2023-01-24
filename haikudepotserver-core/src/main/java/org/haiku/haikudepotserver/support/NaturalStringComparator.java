/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Comparator;

/**
 * <p>This comparator aims to match the behaviour of the C++ implementation in BPrivate::NaturalCompare(..).  This
 * comparison mechanism is used to compare two elements of a version.  This is because the, for example, major
 * component of the version may contain alpha numeric values as well as underscores (it is a string) and so a
 * lexicographical comparison is not a logical choice.</p>
 */

class NaturalStringComparator implements Comparator<String> {

    private final NaturalChunkFormat naturalChunkFormat = new NaturalChunkFormat();

    @Override
    public int compare(String o1, String o2) {

        if (null == o1) {
            return null == o2 ? 0 : -1;
        }

        if (null == o2) {
            return 1;
        }

        ParsePosition pos1 = new ParsePosition(0);
        ParsePosition pos2 = new ParsePosition(0);

        while(true) {

            NaturalChunk nc1 = (NaturalChunk) naturalChunkFormat.parseObject(o1, pos1);
            NaturalChunk nc2 = (NaturalChunk) naturalChunkFormat.parseObject(o2, pos2);

            if (NaturalChunk.Type.END == nc1.getType()) {
                return NaturalChunk.Type.END == nc2.getType() ? 0 : -1;
            }

            if (NaturalChunk.Type.END == nc2.getType()) {
                return 1;
            }

            // different types then just compare from here to the end of the string.

            if (nc1.getType() != nc2.getType()) {
                String remainder1 = nc1.getSource().substring(nc1.getOffset());
                String remainder2 = nc2.getSource().substring(nc2.getOffset());
                return remainder1.compareToIgnoreCase(remainder2);
            }

            switch (nc1.getType()) {
                case NUMBER -> {
                    if (nc1.getLength() > nc2.getLength()) {
                        return 1;
                    }

                    if (nc1.getLength() < nc2.getLength()) {
                        return -1;
                    }

                    int result = nc1.getExtract().compareTo(nc2.getExtract());

                    if (0 != result) {
                        return result;
                    }
                }
                case ASCII -> {
                    int result = nc1.getExtract().compareToIgnoreCase(nc2.getExtract());

                    if (0 != result) {
                        return result;
                    }
                }
                default -> throw new IllegalStateException("unhandled natural chunk type; " + nc1.getType());
            }
        }
    }

    /**
     * <p>This method will pull out chunks of the text in order that they can be compared in the comparator.</p>
     */

    private static class NaturalChunkFormat extends Format {

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            throw new UnsupportedOperationException();
        }

        private boolean isCharacterOf(NaturalChunk.Type type, char c) {
            return switch (type) {
                case NUMBER -> Character.isDigit(c);
                case ASCII -> !Character.isDigit(c) && !Character.isWhitespace(c);
                default -> throw new IllegalStateException("unhandled natural chunk type; " + type.name());
            };
        }

        @Override
        public Object parseObject(String source, ParsePosition pos) {
            Preconditions.checkNotNull(source);
            Preconditions.checkNotNull(pos);

            // skip any whitespace.

            while (pos.getIndex() < source.length() && Character.isWhitespace(source.charAt(pos.getIndex()))) {
                pos.setIndex(pos.getIndex() + 1);
            }

            // if the end has been met then return end.

            if(pos.getIndex() >= source.length()) {
                return new NaturalChunk(NaturalChunk.Type.END, source, pos.getIndex(), 0);
            }

            NaturalChunk.Type type = Character.isDigit(source.charAt(pos.getIndex())) ? NaturalChunk.Type.NUMBER : NaturalChunk.Type.ASCII;

            // if the type is numeric then skip past any initial zeros, but don't do this if it
            // is actually only zeros before it hits something that is not a zero or a non-numeric.

            if(NaturalChunk.Type.NUMBER == type) {

                int i = pos.getIndex();

                while(i < source.length() && '0'==source.charAt(i)) {
                    i++;
                }

                if(i >= source.length() || !isCharacterOf(NaturalChunk.Type.NUMBER, source.charAt(i))) {
                    pos.setIndex(i-1);
                }
                else {
                    pos.setIndex(i);
                }
            }

            int start = pos.getIndex();

            do {
                pos.setIndex(pos.getIndex()+1);
            }
            while (pos.getIndex() < source.length() && isCharacterOf(type, source.charAt(pos.getIndex())));

            NaturalChunk naturalChunk = new NaturalChunk(type, source, start, pos.getIndex()-start);

            // go past the trailing whitespace here as well.

            while (pos.getIndex() < source.length() && Character.isWhitespace(source.charAt(pos.getIndex()))) {
                pos.setIndex(pos.getIndex()+1);
            }

            return naturalChunk;
        }

    }

    private static class NaturalChunk {

        enum Type {
            NUMBER,
            ASCII, // the use of ascii here is not clear, but follows the convention of the C++ implementation
            END
        }

        private final Type type;
        private final String source;
        private final int offset;
        private final int length;

        NaturalChunk(Type type, String source, int offset, int length) {
            Preconditions.checkNotNull(type);
            Preconditions.checkNotNull(source);
            Preconditions.checkArgument(offset >= 0);
            Preconditions.checkArgument(length >= 0);
            this.type = type;
            this.source = source;
            this.offset = offset;
            this.length = length;
        }

        public Type getType() {
            return type;
        }

        public String getSource() {
            return source;
        }

        public int getOffset() {
            return offset;
        }

        public int getLength() {
            return length;
        }

        String getExtract() {
            return getSource().substring(getOffset(), getOffset() + getLength());
        }

        @Override
        public String toString() {
            return String.format(
                    "%s {%d,%d}",
                    getType().name(),
                    getOffset(),
                    getLength());
        }

    }

}
