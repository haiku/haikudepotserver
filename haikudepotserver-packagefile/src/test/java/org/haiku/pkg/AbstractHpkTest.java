/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

abstract class AbstractHpkTest {

    @TempDir
    File temporaryFolder;

    /**
     * <p>This will copy the supplied test classpath resource into a temporary file to work with during the test.  It
     * is the responsibility of the caller to clean up the temporary file afterwards.</p>
     */

    File prepareTestFile(String resource) throws IOException {
        Preconditions.checkState(null != temporaryFolder);
        byte[] payload = Resources.toByteArray(Resources.getResource(resource));
        File temporaryFile = new File(temporaryFolder, resource);
        Files.write(payload, temporaryFile);
        return temporaryFile;
    }

}
