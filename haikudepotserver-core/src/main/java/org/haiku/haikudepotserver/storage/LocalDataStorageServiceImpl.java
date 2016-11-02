/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.storage;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.Optional;

/**
 * <p>An implementation of the {@link DataStorageService}
 * which stores job data to a local temporary directory.</p>
 */

public class LocalDataStorageServiceImpl implements DataStorageService {

    protected static Logger LOGGER = LoggerFactory.getLogger(LocalDataStorageServiceImpl.class);

    private final static String PATH_APPTMPDIR = "haikudepotserver-data";

    private File tmpDir;

    @Value("${deployment.isproduction:false}")
    private Boolean isProduction;

    @PostConstruct
    public void init() {
        String platformTmpDirPath = System.getProperty("java.io.tmpdir");

        if(Strings.isNullOrEmpty(platformTmpDirPath)) {
            throw new IllegalStateException("unable to ascertain the java temporary directory");
        }

        tmpDir = new File(
                platformTmpDirPath,
                PATH_APPTMPDIR + (null==isProduction||!isProduction ? "-test" : ""));

        if(!tmpDir.exists()) {
            if(tmpDir.mkdirs()) {
               LOGGER.info("did create the application temporary directory path; {}", tmpDir.getAbsolutePath());
            }
            else {
                throw new IllegalStateException("unable to create the application temporary directory path; " + tmpDir.getAbsolutePath());
            }
        }
        else {
            LOGGER.info(
                    "{} files already exist in the application temporary directory; {}",
                    tmpDir.list().length,
                    tmpDir.getAbsolutePath());
        }

    }

    private File fileForKey(String key) {
       return new File(tmpDir, key + ".dat");
    }

    @Override
    public ByteSink put(final String key) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(key));

        return new ByteSink() {

            @Override
            public OutputStream openStream() throws IOException {
                return new FileOutputStream(fileForKey(key));
            }

        };

    }

    @Override
    public Optional<? extends ByteSource> get(final String key) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(key));

        return Optional.of(new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                File f = fileForKey(key);

                if(!f.exists()) {
                    return null;
                }

                return new FileInputStream(f);
            }
        });

    }

    @Override
    public boolean remove(String key) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(key));
        File f = fileForKey(key);

        if(f.exists()) {
            return f.delete();
        }

        return true;
    }

    @Override
    public void clear() {
        LOGGER.info("will clear");

        for(File f : tmpDir.listFiles()) {
            if(f.isFile()) {
                if(f.delete()) {
                    LOGGER.info("did delete; {}", f.getAbsolutePath());
                }
                else {
                    LOGGER.error("was not able to delete; {}", f.getAbsolutePath());
                }
            }
        }

        LOGGER.info("did clear");
    }

}
