/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.support;

/**
 * <p>This is thrown in the API when the user has requested some data and has exceeded a pre-agreed limit.</p>
 */

public class LimitExceededException extends Exception {

    public LimitExceededException() {
    }

}
