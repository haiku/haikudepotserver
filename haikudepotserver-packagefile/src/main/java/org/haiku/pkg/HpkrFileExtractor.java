/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.base.Preconditions;
import org.haiku.pkg.heap.HpkHeapReader;
import org.haiku.pkg.heap.HeapCompression;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * <p>This object represents an object that can extract an Hpkr (Haiku Pkg Repository) file.  If you are wanting to
 * read HPKR files then you should instantiate an instance of this class and then make method calls to it in order to
 * read values such as the attributes of the HPKR file.</p>
 */

public class HpkrFileExtractor implements Closeable {

    private File file;

    private HpkrHeader header;

    private HpkHeapReader heapReader;

    private HpkStringTable attributesStringTable;

    public HpkrFileExtractor(File file) throws IOException, HpkException {

        super();
        Preconditions.checkNotNull(file);
        Preconditions.checkState(file.isFile() && file.exists(), "the file does not exist or is not a file");

        this.file = file;
        this.header = readHeader();

        try {
            heapReader = new HpkHeapReader(
                    file,
                    header.getHeapCompression(),
                    header.getHeaderSize(),
                    header.getHeapChunkSize(), // uncompressed size
                    header.getHeapSizeCompressed(), // including the compressed chunk lengths.
                    header.getHeapSizeUncompressed() // excludes the compressed chunk lengths.
            );

            attributesStringTable = new HpkStringTable(
                    heapReader,
                    header.getInfoLength(),
                    header.getPackagesStringsLength(),
                    header.getPackagesStringsCount());

        }
        catch (Exception e) {
            close();
            throw new HpkException("unable to setup the hpkr file extractor",e);
        }
        catch (Throwable th) {
            close();
            throw new RuntimeException("unable to setup the hpkr file extractor",th);
        }
    }

    @Override
    public void close() {
        if (null != heapReader) {
            heapReader.close();
        }
    }

    public AttributeContext getAttributeContext() {
        AttributeContext context = new AttributeContext();
        context.setHeapReader(heapReader);
        context.setStringTable(attributesStringTable);
        return context;
    }

    public AttributeIterator getPackageAttributesIterator() {
        long offset = header.getInfoLength() + header.getPackagesStringsLength();
        return new AttributeIterator(getAttributeContext(),offset);
    }

    private HpkrHeader readHeader() throws IOException, HpkException {
        Preconditions.checkNotNull(file);

        RandomAccessFile randomAccessFile = null;
        FileHelper fileHelper = new FileHelper();

        try {
            randomAccessFile = new RandomAccessFile(file, "r");

            if (!Arrays.equals(new char[] {'h', 'p', 'k', 'r'}, fileHelper.readMagic(randomAccessFile))) {
                throw new HpkException("magic incorrect at the start of the hpkr file");
            }

            HpkrHeader result = new HpkrHeader();

            result.setHeaderSize(fileHelper.readUnsignedShortToInt(randomAccessFile));
            result.setVersion(fileHelper.readUnsignedShortToInt(randomAccessFile));
            result.setTotalSize(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setMinorVersion(fileHelper.readUnsignedShortToInt(randomAccessFile));

            int compression = fileHelper.readUnsignedShortToInt(randomAccessFile);

            // heap information
            switch (compression) {
                case 0:
                    result.setHeapCompression(HeapCompression.NONE);
                    break;

                case 1:
                    result.setHeapCompression(HeapCompression.ZLIB);
                    break;

                default:
                    throw new HpkException("unknown compression setting in header; "+compression);
            }

            result.setHeapChunkSize(fileHelper.readUnsignedIntToLong(randomAccessFile));
            result.setHeapSizeCompressed(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setHeapSizeUncompressed(fileHelper.readUnsignedLongToLong(randomAccessFile));

            // repository info
            result.setInfoLength(fileHelper.readUnsignedIntToLong(randomAccessFile));
            randomAccessFile.skipBytes(4); // reserved

            // package attributes section
            result.setPackagesLength(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setPackagesStringsLength(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setPackagesStringsCount(fileHelper.readUnsignedLongToLong(randomAccessFile));

            return result;
        } finally {
            if (null!=randomAccessFile) {
                try {
                    randomAccessFile.close();
                }
                catch(IOException ioe) {
                    // ignore
                }
            }
        }
    }

}
