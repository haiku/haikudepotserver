/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import org.haiku.pkg.heap.HeapCompression;

public class HpkrHeader {

    private long headerSize;
    private int version;
    private long totalSize;
    private int minorVersion;

    // heap
    private HeapCompression heapCompression;
    private long heapChunkSize;
    private long heapSizeCompressed;
    private long heapSizeUncompressed;

    // repository info section
    private long infoLength;

    // package attributes section
    private long packagesLength;
    private long packagesStringsLength;
    private long packagesStringsCount;

    public long getHeaderSize() {
        return headerSize;
    }

    public void setHeaderSize(long headerSize) {
        this.headerSize = headerSize;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public void setMinorVersion(int minorVersion) {
        this.minorVersion = minorVersion;
    }

    public HeapCompression getHeapCompression() {
        return heapCompression;
    }

    public void setHeapCompression(HeapCompression heapCompression) {
        this.heapCompression = heapCompression;
    }

    public long getHeapChunkSize() {
        return heapChunkSize;
    }

    public void setHeapChunkSize(long heapChunkSize) {
        this.heapChunkSize = heapChunkSize;
    }

    public long getHeapSizeCompressed() {
        return heapSizeCompressed;
    }

    public void setHeapSizeCompressed(long heapSizeCompressed) {
        this.heapSizeCompressed = heapSizeCompressed;
    }

    public long getHeapSizeUncompressed() {
        return heapSizeUncompressed;
    }

    public void setHeapSizeUncompressed(long heapSizeUncompressed) {
        this.heapSizeUncompressed = heapSizeUncompressed;
    }

    public long getInfoLength() {
        return infoLength;
    }

    public void setInfoLength(long infoLength) {
        this.infoLength = infoLength;
    }

    public long getPackagesLength() {
        return packagesLength;
    }

    public void setPackagesLength(long packagesLength) {
        this.packagesLength = packagesLength;
    }

    public long getPackagesStringsLength() {
        return packagesStringsLength;
    }

    public void setPackagesStringsLength(long packagesStringsLength) {
        this.packagesStringsLength = packagesStringsLength;
    }

    public long getPackagesStringsCount() {
        return packagesStringsCount;
    }

    public void setPackagesStringsCount(long packagesStringsCount) {
        this.packagesStringsCount = packagesStringsCount;
    }

}

