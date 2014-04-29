/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg.heap;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.haikuos.pkg.FileHelper;
import org.haikuos.pkg.HpkException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * <P>An instance of this class is able to read the heap's chunks that are in HPK format.  Note
 * that this class will also take responsibility for caching the chunks so that a subsequent
 * read from the same chunk will not require a re-fault from disk.</P>
 */

public class HpkHeapReader implements Closeable, HeapReader {

    private HeapCompression compression;

    private long heapOffset;

    private long chunkSize;

    private long compressedSize; // including the shorts for the chunks' compressed sizes

    private long uncompressedSize; // excluding the shorts for the chunks' compressed sizes

    private LoadingCache<Integer,byte[]> heapChunkUncompressedCache;

    private int[] heapChunkCompressedLengths = null;

    private RandomAccessFile randomAccessFile;

    private FileHelper fileHelper = new FileHelper();

    public HpkHeapReader(
            final File file,
            final HeapCompression compression,
            final long heapOffset,
            final long chunkSize,
            final long compressedSize,
            final long uncompressedSize) throws HpkException {

        super();

        Preconditions.checkNotNull(file);
        Preconditions.checkNotNull(compression);
        Preconditions.checkState(heapOffset > 0 &&heapOffset < Integer.MAX_VALUE);
        Preconditions.checkState(chunkSize > 0 && chunkSize < Integer.MAX_VALUE);
        Preconditions.checkState(compressedSize >= 0 && compressedSize < Integer.MAX_VALUE);
        Preconditions.checkState(uncompressedSize >= 0 && compressedSize < Integer.MAX_VALUE);

        this.compression = compression;
        this.heapOffset = heapOffset;
        this.chunkSize = chunkSize;
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;

        try {
            randomAccessFile = new RandomAccessFile(file,"r");

            heapChunkCompressedLengths = new int[getHeapChunkCount()];
            populateChunkCompressedLengths(heapChunkCompressedLengths);

            heapChunkUncompressedCache = CacheBuilder
                    .newBuilder()
                    .maximumSize(3)
                    .build(new CacheLoader<Integer, byte[]>() {
                        @Override
                        public byte[] load(@SuppressWarnings("NullableProblems") Integer key) throws Exception {
                            Preconditions.checkNotNull(key);

                            // TODO: best to avoid continuously allocating new byte buffers
                            byte[] result = new byte[getHeapChunkUncompressedLength(key)];
                            readHeapChunk(key,result);
                            return result;
                        }
                    });
        }
        catch(Exception e) {
            close();
            throw new HpkException("unable to configure the hpk heap reader",e);
        }
        catch(Throwable th) {
            close();
            throw new RuntimeException("unable to configure the hkp heap reader",th);
        }

    }

    @Override
    public void close() {
        if(null!=randomAccessFile) {
            try {
                randomAccessFile.close();
            }
            catch(IOException ioe) {
                // ignore
            }
        }
    }

    /**
     * <p>This gives the quantity of chunks that are in the heap.</p>
     */

    private int getHeapChunkCount() {
        int count = (int) (uncompressedSize / chunkSize);

        if(0!=uncompressedSize % chunkSize) {
            count++;
        }

        return count;
    }

    private int getHeapChunkUncompressedLength(int index) {
        if(index < getHeapChunkCount()-1) {
            return (int) chunkSize;
        }

        return (int) (uncompressedSize - (chunkSize * (getHeapChunkCount() - 1)));
    }

    private int getHeapChunkCompressedLength(int index) throws IOException, HpkException {
        return heapChunkCompressedLengths[index];
    }

    /**
     * <p>After the chunk data is a whole lot of unsigned shorts that define the compressed
     * size of the chunks in the heap.  This method will shift the input stream to the
     * start of those shorts and read them in.</p>
     */

    private void populateChunkCompressedLengths(int lengths[]) throws IOException, HpkException {
        Preconditions.checkNotNull(lengths);

        int count = getHeapChunkCount();
        long totalCompressedLength = 0;
        randomAccessFile.seek(heapOffset + compressedSize - (2 * (count-1)));

        for(int i=0;i<count-1;i++) {

            // C++ code says that the stored size is length of chunk -1.
            lengths[i] = fileHelper.readUnsignedShortToInt(randomAccessFile) + 1;

            if(lengths[i] > uncompressedSize) {
                throw new HpkException(
                        String.format("the chunk at %d is of size %d, but the uncompressed length of the chunks is %d",
                                i,
                                lengths[i],
                                uncompressedSize));
            }

            totalCompressedLength += lengths[i];
        }

        // the last one will be missing will need to be derived
        lengths[count-1] = (int) (compressedSize - ((2*(count-1)) + totalCompressedLength));

        if(lengths[count-1] < 0 || lengths[count-1] > uncompressedSize) {
            throw new HpkException(
                    String.format(
                            "the derivation of the last chunk size of %d is out of bounds",
                            lengths[count-1]));
        }

        //totalCompressedLength += lengths[count-1];
    }

    private boolean isHeapChunkCompressed(int index) throws IOException, HpkException {
        return getHeapChunkCompressedLength(index) < getHeapChunkUncompressedLength(index);
    }

    private long getHeapChunkAbsoluteFileOffset(int index) throws IOException, HpkException {
        long result = heapOffset; // heap comes after the header.

        for(int i=0;i<index;i++) {
            result += getHeapChunkCompressedLength(i);
        }

        return result;
    }

    /**
     * <p>This will read from the current offset into the supplied buffer until the supplied buffer is completely
     * filledup.</p>
     */

    private void readFully(byte[] buffer) throws IOException, HpkException {
        Preconditions.checkNotNull(buffer);
        int total = 0;

        while(total < buffer.length) {
            int read = randomAccessFile.read(buffer,total,buffer.length - total);

            if(-1==read) {
                throw new HpkException("unexpected end of file when reading a chunk");
            }

            total += read;
        }
    }

    /**
     * <p>This will read a chunk of the heap into the supplied buffer.  It is assumed that the buffer will be
     * of the correct length for the uncompressed heap chunk size.</p>
     */

    private void readHeapChunk(int index, byte[] buffer) throws IOException, HpkException {

        randomAccessFile.seek(getHeapChunkAbsoluteFileOffset(index));
        int chunkUncompressedLength = getHeapChunkUncompressedLength(index);

        if(isHeapChunkCompressed(index) || HeapCompression.NONE == compression) {

            switch(compression) {
                case NONE:
                    throw new IllegalStateException();

                case ZLIB:
                {
                    byte[] deflatedBuffer = new byte[getHeapChunkCompressedLength(index)];
                    readFully(deflatedBuffer);

                    Inflater inflater = new Inflater();
                    inflater.setInput(deflatedBuffer);

                    try {
                        int read;

                        if(chunkUncompressedLength != (read = inflater.inflate(buffer))) {

                            // the last chunk size uncompressed may be smaller than the chunk size,
                            // so don't throw an exception if this happens.

                            if(index < getHeapChunkCount()-1) {
                                String message = String.format("a compressed heap chunk inflated to %d bytes; was expecting %d",read,chunkUncompressedLength);

                                if(inflater.needsInput()) {
                                    message += "; needs input";
                                }

                                if(inflater.needsDictionary()) {
                                    message += "; needs dictionary";
                                }

                                throw new HpkException(message);
                            }
                        }

                        if(!inflater.finished()) {
                            throw new HpkException(String.format("incomplete inflation of input data while reading chunk %d",index));
                        }
                    }
                    catch(DataFormatException dfe) {
                        throw new HpkException("unable to inflate (decompress) heap chunk "+index,dfe);
                    }
                }
                break;

                default:
                    throw new IllegalStateException("unsupported compression; "+compression);
            }
        }
        else {
            int read;

            if(chunkUncompressedLength != (read = randomAccessFile.read(buffer,0,chunkUncompressedLength))) {
                throw new HpkException(String.format("problem reading chunk %d of heap; only read %d of %d bytes",index,read,buffer.length));
            }
        }
    }

    @Override
    public int readHeap(long offset) {
        Preconditions.checkState(offset >= 0);
        Preconditions.checkState(offset < uncompressedSize);

        int chunkIndex = (int) (offset / chunkSize);
        int chunkOffset = (int) (offset - (chunkIndex * chunkSize));
        byte[] chunkData = heapChunkUncompressedCache.getUnchecked(chunkIndex);

        return chunkData[chunkOffset] & 0xff;
    }

    @Override
    public void readHeap(byte[] buffer, int bufferOffset, HeapCoordinates coordinates) {

        Preconditions.checkNotNull(buffer);
        Preconditions.checkState(bufferOffset >= 0);
        Preconditions.checkState(bufferOffset < buffer.length);
        Preconditions.checkState(coordinates.getOffset() >= 0);
        Preconditions.checkState(coordinates.getOffset() < uncompressedSize);
        Preconditions.checkState(coordinates.getOffset()+coordinates.getLength() < uncompressedSize);

        // first figure out how much to read from this chunk

        int chunkIndex = (int) (coordinates.getOffset() / chunkSize);
        int chunkOffset = (int) (coordinates.getOffset() - (chunkIndex * chunkSize));
        int chunkLength;
        int chunkUncompressedLength = getHeapChunkUncompressedLength(chunkIndex);

        if(chunkOffset + coordinates.getLength() > chunkUncompressedLength) {
            chunkLength = (chunkUncompressedLength - chunkOffset);
        }
        else {
            chunkLength = (int) coordinates.getLength();
        }

        // now read it in.

        byte[] chunkData = heapChunkUncompressedCache.getUnchecked(chunkIndex);

        System.arraycopy(chunkData,chunkOffset,buffer,bufferOffset,chunkLength);

        // if we need to get some more data from the next chunk then call again.
        // TODO - recursive approach may not be too good when more data is involved; probably ok for hpkr though.

        if(chunkLength < coordinates.getLength()) {
            readHeap(
                    buffer,
                    bufferOffset + chunkLength,
                    new HeapCoordinates(
                            coordinates.getOffset() + chunkLength,
                            coordinates.getLength() - chunkLength));
        }

    }

}
