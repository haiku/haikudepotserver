/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import org.haiku.pkg.heap.HeapReader;

/**
 * <p>This object carries around pointers to other data structures and model objects that are required to
 * support the processing of attributes.</p>
 */

public class AttributeContext {

    private StringTable stringTable;

    private HeapReader heapReader;

    public HeapReader getHeapReader() {
        return heapReader;
    }

    public void setHeapReader(HeapReader heapReader) {
        this.heapReader = heapReader;
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    public void setStringTable(StringTable stringTable) {
        this.stringTable = stringTable;
    }

}
