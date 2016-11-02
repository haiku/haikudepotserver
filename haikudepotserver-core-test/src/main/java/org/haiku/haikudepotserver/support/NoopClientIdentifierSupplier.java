/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import java.util.Optional;

public class NoopClientIdentifierSupplier implements ClientIdentifierSupplier {

    @Override
    public Optional<String> get() {
        return Optional.empty();
    }

}
