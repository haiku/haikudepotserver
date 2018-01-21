/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.hvif;

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
 * <p>Renders the HVIF vector icon to PNG using the command line tool that can be built as part of the Haiku
 * build process.  See the accompanying documentation for help with this.</p>
 */

class Hvif2PngHvifRenderingServiceImpl
        extends AbstractExternalToolService<Hvif2PngHvifRenderingServiceImpl.Context>
        implements HvifRenderingService {

    protected static Logger LOGGER = LoggerFactory.getLogger(Hvif2PngHvifRenderingServiceImpl.class);

    private final String hvif2pngPath;

    Hvif2PngHvifRenderingServiceImpl(String hvif2pngPath) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(hvif2pngPath), "the path is required");
        this.hvif2pngPath = hvif2pngPath;
    }

    @Override
    public List<String> createArguments(
            Hvif2PngHvifRenderingServiceImpl.Context context,
            File temporaryInputFile,
            File temporaryOutputFile) {
        return ImmutableList.of(
                hvif2pngPath,
                "-s", Integer.toString(context.getSize()),
                "-i", temporaryInputFile.getAbsolutePath(),
                "-o", temporaryOutputFile.getAbsolutePath()
        );
    }

    @Override
    public byte[] render(int size, byte[] input) throws IOException {
        return execute(new Context(size), input);
    }

    static class Context {

        private int size;

        Context(int size) {
            this.size = size;
        }

        public int getSize() {
            return size;
        }
    }

}
