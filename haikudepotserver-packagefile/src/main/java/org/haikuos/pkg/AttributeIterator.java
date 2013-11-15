/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.haikuos.pkg.heap.HeapCoordinates;
import org.haikuos.pkg.model.*;

import java.math.BigInteger;
import java.util.Iterator;

/**
 * <p>This object is able to provide an iterator through all of the attributes at a given offset in the chunks.  The
 * chunk data is supplied through an instance of {@link AttributeContext}.  It will work through all of the
 * attributes serially and will also process all of the child-attributes as well.  The iteration process means that
 * less in-memory data is required to process a relatively long list of attributes.</p>
 *
 * <p>Use the method {@link #hasNext()} to find out if there is another attribute to read and {@link #next()} in
 * order to obtain the next attribute.</p>
 *
 * <p>Note that this does not actually implement {@link Iterator} because it needs to throw Hpk exceptions
 * which would mean that it were not compliant with the @{link Iterator} interface.</p>
 */

public class AttributeIterator {

    private final static int ATTRIBUTE_TYPE_INVALID = 0;
    private final static int ATTRIBUTE_TYPE_INT = 1;
    private final static int ATTRIBUTE_TYPE_UINT = 2;
    private final static int ATTRIBUTE_TYPE_STRING = 3;
    private final static int ATTRIBUTE_TYPE_RAW = 4;

    private final static int ATTRIBUTE_ENCODING_INT_8_BIT = 0;
    private final static int ATTRIBUTE_ENCODING_INT_16_BIT = 1;
    private final static int ATTRIBUTE_ENCODING_INT_32_BIT = 2;
    private final static int ATTRIBUTE_ENCODING_INT_64_BIT = 3;

    private final static int ATTRIBUTE_ENCODING_STRING_INLINE = 0;
    private final static int ATTRIBUTE_ENCODING_STRING_TABLE = 1;

    private final static int ATTRIBUTE_ENCODING_RAW_INLINE = 0;
    private final static int ATTRIBUTE_ENCODING_RAW_HEAP = 1;

    private long offset;

    private AttributeContext context;

    private BigInteger nextTag = null;

    public AttributeIterator(AttributeContext context, long offset) {
        super();

        Preconditions.checkNotNull(context);
        Preconditions.checkState(offset >= 0 && offset < Integer.MAX_VALUE);

        this.offset = offset;
        this.context = context;
    }

    public AttributeContext getContext() {
        return context;
    }

    public long getOffset() {
        return offset;
    }

    private int deriveAttributeTagType(BigInteger tag) {
        return tag.subtract(BigInteger.valueOf(1l)).shiftRight(7).and(BigInteger.valueOf(0x7l)).intValue();
    }

    private int deriveAttributeTagId(BigInteger tag) {
        return tag.subtract(BigInteger.valueOf(1l)).and(BigInteger.valueOf(0x7fl)).intValue();
    }

    private int deriveAttributeTagEncoding(BigInteger tag) {
        return tag.subtract(BigInteger.valueOf(1l)).shiftRight(11).and(BigInteger.valueOf(3l)).intValue();
    }

    private boolean deriveAttributeTagHasChildAttributes(BigInteger tag) {
        return 0!=tag.subtract(BigInteger.valueOf(1l)).shiftRight(10).and(BigInteger.valueOf(1l)).intValue();
    }

    private BigInteger getNextTag() {
        if(null==nextTag) {
            nextTag = readUnsignedLeb128();
        }

        return nextTag;
    }

    private BigInteger readUnsignedLeb128() {
        BigInteger result = BigInteger.valueOf(0l);
        int shift = 0;

        while(true) {
            int b = context.getHeapReader().readHeap(offset);
            offset++;

            result = result.or(BigInteger.valueOf((long) (b & 0x7f)).shiftLeft(shift));

            if(0 == (b & 0x80)) {
                return result;
            }

            shift+=7;
        }
    }

    private void ensureValidEncodingForInt(int encoding) {
        switch(encoding) {
            case ATTRIBUTE_ENCODING_INT_8_BIT:
            case ATTRIBUTE_ENCODING_INT_16_BIT:
            case ATTRIBUTE_ENCODING_INT_32_BIT:
            case ATTRIBUTE_ENCODING_INT_64_BIT:
                break;

            default:
                throw new IllegalStateException("unknown encoding on a signed integer");
        }
    }

    /**
     * <p>This method allows the caller to discover if there is another attribute to get off the iterator.</p>
     */

    public boolean hasNext() {
        return 0!=getNextTag().signum();
    }

    /**
     * <p>This method will return the next {@link org.haikuos.pkg.model.Attribute}.  If there is not another value to return then
     * this method will return null.  It will throw an instance of @{link HpkException} in any situation in which
     * it is not able to parse the data or chunks such that it is not able to read the next attribute.</p>
     */

    public Attribute next() throws HpkException {

        Attribute result = null;

        // first, the LEB128 has to be read in which is the 'tag' defining what sort of attribute this is that
        // we are dealing with.

        BigInteger tag = getNextTag();

        // if we encounter 0 tag then we know that we have finished the list.

        if(0!=tag.signum()) {

            int encoding = deriveAttributeTagEncoding(tag);
            int id = deriveAttributeTagId(tag);

            if(id <= 0 || id >= AttributeId.values().length) {
                throw new HpkException("illegal id; "+Integer.toString(id));
            }
            AttributeId attributeId = AttributeId.values()[id];

            switch(deriveAttributeTagType(tag)) {

                case ATTRIBUTE_TYPE_INVALID:
                    throw new HpkException("an invalid attribute tag type has been encountered");

                case ATTRIBUTE_TYPE_INT:
                {
                    ensureValidEncodingForInt(encoding);
                    byte[] buffer = new byte[encoding+1];
                    context.getHeapReader().readHeap(buffer,0,new HeapCoordinates(offset,encoding+1));
                    offset+=encoding+1;
                    result = new IntAttribute(attributeId, new BigInteger(buffer));
                }
                break;

                case ATTRIBUTE_TYPE_UINT:
                {
                    ensureValidEncodingForInt(encoding);
                    byte[] buffer = new byte[encoding+1];
                    context.getHeapReader().readHeap(buffer,0,new HeapCoordinates(offset,encoding+1));
                    offset+=encoding+1;
                    result = new IntAttribute(attributeId, new BigInteger(1,buffer));
                }
                break;

                case ATTRIBUTE_TYPE_STRING:
                {
                    switch(encoding) {

                        case ATTRIBUTE_ENCODING_STRING_INLINE:
                        {
                            ByteArrayDataOutput assembly = ByteStreams.newDataOutput();

                            while(null==result) {
                                int b = context.getHeapReader().readHeap(offset);
                                offset++;

                                if(0!=b) {
                                    assembly.write(b);
                                }
                                else {
                                    result = new StringInlineAttribute(
                                            attributeId,
                                            new String(
                                                    assembly.toByteArray(),
                                                    Charsets.UTF_8));
                                }
                            }
                        }
                        break;

                        case ATTRIBUTE_ENCODING_STRING_TABLE:
                        {
                            BigInteger index = readUnsignedLeb128();

                            if(index.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                                throw new IllegalStateException("the string table index is preposterously large");
                            }

                            result = new StringTableRefAttribute(attributeId,index.intValue());
                        }
                        break;

                        default:
                            throw new HpkException("unknown string encoding; "+encoding);
                    }
                }
                break;

                case ATTRIBUTE_TYPE_RAW:
                {
                    switch(encoding) {
                        case ATTRIBUTE_ENCODING_RAW_INLINE:
                        {
                            BigInteger length = readUnsignedLeb128();

                            if(length.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                                throw new HpkException("the length of the inline data is too large");
                            }

                            byte[] buffer = new byte[length.intValue()];
                            context.getHeapReader().readHeap(buffer,0,new HeapCoordinates(offset,length.intValue()));
                            offset += length.intValue();

                            result = new RawInlineAttribute(attributeId, buffer);
                        }
                        break;

                        case ATTRIBUTE_ENCODING_RAW_HEAP:
                        {
                            BigInteger rawLength = readUnsignedLeb128();
                            BigInteger rawOffset = readUnsignedLeb128();

                            if(rawLength.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                                throw new HpkException("the length of the heap data is too large");
                            }

                            if(rawOffset.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                                throw new HpkException("the offset of the heap data is too large");
                            }

                            result = new RawHeapAttribute(
                                    attributeId,
                                    new HeapCoordinates(
                                            rawOffset.longValue(),
                                            rawLength.longValue()));
                        }

                        default:
                            throw new HpkException("unknown raw encoding; "+encoding);
                    }
                }
                break;

                default:
                    throw new HpkException("unable to read the tag type; "+deriveAttributeTagType(tag));

            }

            // each attribute id has a type associated with it; now check that the attribute matches
            // its intended type.

            if(result.getAttributeId().getAttributeType() != result.getAttributeType()) {
                throw new HpkException(String.format(
                        "mismatch in attribute type for id %s; expecting %s, but got %s",
                        result.getAttributeId().getName(),
                        result.getAttributeId().getAttributeType(),
                        result.getAttributeType()));
            }

            // possibly there are child attributes after this attribute; if this is the
            // case then open-up a new iterator to work across those and load them in.

            if(deriveAttributeTagHasChildAttributes(tag)) {

                AttributeIterator childAttributeIterator = new AttributeIterator(context, offset);

                while(childAttributeIterator.hasNext()) {
                    result.addChildAttribute(childAttributeIterator.next());
                }

                offset = childAttributeIterator.getOffset();

            }

            nextTag = null;
        }

        return result;
    }

}
