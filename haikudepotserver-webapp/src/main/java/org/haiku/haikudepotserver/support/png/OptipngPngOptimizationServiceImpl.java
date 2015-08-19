/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.png;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.haiku.haikudepotserver.support.AbstractExternalToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * <p>This class provides a service to optimize PNG images.</p>
 */

public class OptipngPngOptimizationServiceImpl
        extends AbstractExternalToolService<OptipngPngOptimizationServiceImpl.Context>
        implements PngOptimizationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(OptipngPngOptimizationServiceImpl.class);

    private String optiPngPath;

    public OptipngPngOptimizationServiceImpl(String optiPngPath) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(optiPngPath), "the path should be supplied");
        this.optiPngPath = optiPngPath;
    }

    public boolean identityOptimization() {
        return false;
    }

    @Override
    public List<String> createArguments(
            OptipngPngOptimizationServiceImpl.Context context,
            File temporaryInputFile,
            File temporaryOutputFile) {
        return ImmutableList.of(
                optiPngPath, "-o" + context.getOptimizationLevel(),
                "-out", temporaryOutputFile.getAbsolutePath(),
                temporaryInputFile.getAbsolutePath()
        );
    }


    @Override
    public byte[] optimize(byte[] input) throws IOException {
        byte[] out = execute(new Context(), input);
        LOGGER.debug("png optimized by {}%", (out.length*100 / input.length));
        return out;
    }

    public static class Context {

        public int optimizationLevel = 5;

        public int getOptimizationLevel() {
            return optimizationLevel;
        }
    }

}
