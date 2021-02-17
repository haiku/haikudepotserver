/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.pkg;

import com.google.common.base.Preconditions;
import org.haiku.pkg.heap.HeapCompression;
import org.haiku.pkg.heap.HpkHeapReader;
import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.FileType;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>This object represents an object that can extract an Hpkg (Haiku Pkg) file.  If you are wanting to
 * read HPKG files then you should instantiate an instance of this class and then make method calls to it in order to
 * read values such as the attributes of the file.</p>
 */

public class HpkgFileExtractor implements Closeable {

    private final File file;

    private final HpkgHeader header;

    private final HpkHeapReader heapReader;

    private final HpkStringTable tocStringTable;

    private final HpkStringTable packageAttributesStringTable;

    public HpkgFileExtractor(File file) throws IOException {

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

            tocStringTable = new HpkStringTable(
                    heapReader,
                    header.getHeapSizeUncompressed()
                            - (header.getPackageAttributesLength() + header.getTocLength()),
                    header.getTocStringsLength(),
                    header.getTocStringsCount());

            packageAttributesStringTable = new HpkStringTable(
                    heapReader,
                    header.getHeapSizeUncompressed() - header.getPackageAttributesLength(),
                    header.getPackageAttributesStringsLength(),
                    header.getPackageAttributesStringsCount());
        }
        catch (Exception e) {
            close();
            throw new HpkException("unable to setup the hpkg file extractor",e);
        }
        catch (Throwable th) {
            close();
            throw new RuntimeException("unable to setup the hpkg file extractor",th);
        }
    }

    @Override
    public void close() {
        if (null != heapReader) {
            heapReader.close();
        }
    }

    public AttributeContext getPackageAttributeContext() {
        AttributeContext context = new AttributeContext();
        context.setHeapReader(heapReader);
        context.setStringTable(packageAttributesStringTable);
        return context;
    }

    public AttributeIterator getPackageAttributesIterator() {
        long offset = (header.getHeapSizeUncompressed() - header.getPackageAttributesLength())
                + header.getPackageAttributesStringsLength();
        return new AttributeIterator(getPackageAttributeContext(), offset);
    }

    public AttributeContext getTocContext() {
        AttributeContext context = new AttributeContext();
        context.setHeapReader(heapReader);
        context.setStringTable(tocStringTable);
        return context;
    }

    public AttributeIterator getTocIterator() {
        long tocOffset = (header.getHeapSizeUncompressed()
                - (header.getPackageAttributesLength() + header.getTocLength()));
        long tocAttributeOffset = tocOffset + header.getTocStringsLength();
        return new AttributeIterator(getTocContext(), tocAttributeOffset);
    }

    public List<Attribute> getToc() {
        List<Attribute> assembly = new ArrayList<>();
        AttributeIterator attributeIterator = getTocIterator();
        while(attributeIterator.hasNext()) {
            assembly.add(attributeIterator.next());
        }
        return Collections.unmodifiableList(assembly);
    }

    private HpkgHeader readHeader() throws IOException {
        Preconditions.checkNotNull(file);
        FileHelper fileHelper = new FileHelper();

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {

            if (fileHelper.getType(randomAccessFile) != FileType.HPKG) {
                throw new HpkException("magic incorrect at the start of the hpkg file");
            }

            HpkgHeader result = new HpkgHeader();

            result.setHeaderSize(fileHelper.readUnsignedShortToInt(randomAccessFile));
            result.setVersion(fileHelper.readUnsignedShortToInt(randomAccessFile));
            result.setTotalSize(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setMinorVersion(fileHelper.readUnsignedShortToInt(randomAccessFile));

            result.setHeapCompression(HeapCompression.getByNumericValue(fileHelper.readUnsignedShortToInt(randomAccessFile)));
            result.setHeapChunkSize(fileHelper.readUnsignedIntToLong(randomAccessFile));
            result.setHeapSizeCompressed(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setHeapSizeUncompressed(fileHelper.readUnsignedLongToLong(randomAccessFile));

            result.setPackageAttributesLength(fileHelper.readUnsignedIntToLong(randomAccessFile));
            result.setPackageAttributesStringsLength(fileHelper.readUnsignedIntToLong(randomAccessFile));
            result.setPackageAttributesStringsCount(fileHelper.readUnsignedIntToLong(randomAccessFile));
            randomAccessFile.skipBytes(4); // reserved uint32

            result.setTocLength(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setTocStringsLength(fileHelper.readUnsignedLongToLong(randomAccessFile));
            result.setTocStringsCount(fileHelper.readUnsignedLongToLong(randomAccessFile));

            return result;
        }
    }

}
