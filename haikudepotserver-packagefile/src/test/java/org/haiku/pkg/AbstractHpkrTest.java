/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractHpkrTest {

    /**
     * <p>This will copy the supplied test classpath resource into a temporary file to work with during the test.  It
     * is the responsibility of the caller to clean up the temporary file afterwards.</p>
     */

    protected File prepareTestFile() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/repo.hpkr");

        if(null==inputStream) {
            throw new IllegalStateException("unable to find the test hpkr resource");
        }

        File temporaryFile = File.createTempFile("repo-test-","hpkr");
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(temporaryFile);
            ByteStreams.copy(inputStream, fileOutputStream);
        }
        catch(IOException ioe) {
            temporaryFile.delete();
            throw new IOException("unable to copy the test hpkr resource to a temporary file; "+temporaryFile.getAbsolutePath());
        }
        finally {
            if(null!=fileOutputStream) {
                try {
                    fileOutputStream.close();
                }
                catch(IOException ioe) {
                    // ignore
                }
            }
        }

        return temporaryFile;
    }

}
