/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.bitmap;

import java.io.IOException;

/**
 * <p>Interface of a service that is able to provide 'common sense' optimizations of PNG images.</p>
 */

public interface PngOptimizationService {

    /**
     * <p>Returns true if the optimization produces an identity result; that it does not actually
     * do anything - a noop.</p>
     */

    boolean identityOptimization();

    /**
     * <p>This method will optimize the data supplied, producing an optimized output file.</p>
     */

    byte[] optimize(byte[] input) throws IOException;

}
