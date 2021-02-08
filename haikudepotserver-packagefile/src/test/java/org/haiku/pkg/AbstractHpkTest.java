/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

abstract class AbstractHpkTest {

    private TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder getTemporaryFolder() {
        return temporaryFolder;
    }

    /**
     * <p>This will copy the supplied test classpath resource into a temporary file to work with during the test.  It
     * is the responsibility of the caller to clean up the temporary file afterwards.</p>
     */

    File prepareTestFile(String resource) throws IOException {
        byte[] payload = Resources.toByteArray(Resources.getResource(resource));
        File temporaryFile = getTemporaryFolder().newFile(resource);
        Files.write(payload, temporaryFile);
        return temporaryFile;
    }

}
