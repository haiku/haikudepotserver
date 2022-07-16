/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model;

/**
 * <p>This type defines the versions that should be sent back in the result.  If the client were
 * only interested in the latest version for example, then it should use the "LATEST" value.</p>
 */
@Deprecated
public enum PkgVersionType {
    ALL,
    LATEST,
    NONE,
    SPECIFIC
}
