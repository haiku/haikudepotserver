/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * <p>Instances of this interface are able to identify a remote
 * client.  This might be something like the remote client's IP
 * address or something like this.</p>
 */

public interface ClientIdentifierSupplier extends Supplier<Optional<String>> {
}
