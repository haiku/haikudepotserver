package org.haiku.pkg;

import org.haiku.pkg.heap.HeapCompression;

public class HpkgHeader {

    private long headerSize;
    private int version;
    private long totalSize;
    private int minorVersion;

    // heap
    private HeapCompression heapCompression;
    private long heapChunkSize;
    private long heapSizeCompressed;
    private long heapSizeUncompressed;

    private long packageAttributesLength;
    private long packageAttributesStringsLength;
    private long packageAttributesStringsCount;

    private long tocLength;
    private long tocStringsLength;
    private long tocStringsCount;

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

    public long getPackageAttributesLength() {
        return packageAttributesLength;
    }

    public void setPackageAttributesLength(long packageAttributesLength) {
        this.packageAttributesLength = packageAttributesLength;
    }

    public long getPackageAttributesStringsLength() {
        return packageAttributesStringsLength;
    }

    public void setPackageAttributesStringsLength(long packageAttributesStringsLength) {
        this.packageAttributesStringsLength = packageAttributesStringsLength;
    }

    public long getPackageAttributesStringsCount() {
        return packageAttributesStringsCount;
    }

    public void setPackageAttributesStringsCount(long packageAttributesStringsCount) {
        this.packageAttributesStringsCount = packageAttributesStringsCount;
    }

    public long getTocLength() {
        return tocLength;
    }

    public void setTocLength(long tocLength) {
        this.tocLength = tocLength;
    }

    public long getTocStringsLength() {
        return tocStringsLength;
    }

    public void setTocStringsLength(long tocStringsLength) {
        this.tocStringsLength = tocStringsLength;
    }

    public long getTocStringsCount() {
        return tocStringsCount;
    }

    public void setTocStringsCount(long tocStringsCount) {
        this.tocStringsCount = tocStringsCount;
    }
}
