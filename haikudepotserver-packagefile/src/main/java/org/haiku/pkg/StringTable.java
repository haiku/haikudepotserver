/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

/**
 * <p>The attribute-reading elements of the system need to be able to access a string table.  This is interface of
 * an object which is able to provide those strings.</p>
 */

public interface StringTable {

    /**
     * <p>Given the index supplied, this method should return the corresponding string.  It will throw an instance
     * of {@link HpkException} if there is any problems associated with achieving this.</p>
     */

    String getString(int index) throws HpkException;

}
