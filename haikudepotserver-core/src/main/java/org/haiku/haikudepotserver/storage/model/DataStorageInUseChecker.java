/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage.model;

import java.util.Collection;
import java.util.Set;

/**
 * <p>Checks to see if one or more data are currently in use. This is used in the
 * data garbage collection process.</p>
 */

public interface DataStorageInUseChecker {

    /**
     * Returns the codes that are currently in-use and therefore should not be
     * deleted.
     * @param codes
     * @return the {@link Set} of codes which are being used.
     */

    Set<String> inUse(Collection<String> codes);

}
