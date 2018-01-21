/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.io.ByteStreams;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.*;

abstract class AbstractHpkrTest {

    private static final String RESOURCE_TEST = "/repo.hpkr";

    private TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder getTemporaryFolder() {
        return temporaryFolder;
    }

    /**
     * <p>This will copy the supplied test classpath resource into a temporary file to work with during the test.  It
     * is the responsibility of the caller to clean up the temporary file afterwards.</p>
     */

    File prepareTestFile() throws IOException {
        File temporaryFile;

        try (InputStream inputStream = getClass().getResourceAsStream(RESOURCE_TEST)) {

            if (null == inputStream) {
                Assert.fail("unable to find the resource [" + RESOURCE_TEST + "]");
            }

            temporaryFile = getTemporaryFolder().newFile("repo-test.hpkr");

            try (OutputStream fileOutputStream = new FileOutputStream(temporaryFile)) {
                ByteStreams.copy(inputStream, fileOutputStream);
            } catch (IOException ioe) {
                throw new IOException(
                        "unable to copy [" + RESOURCE_TEST + "] to a temporary file ["
                                + temporaryFile.getAbsolutePath() + "]", ioe);
            }
        }

        return temporaryFile;
    }

}
