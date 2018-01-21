/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.tool;

import org.haiku.pkg.HpkrFileExtractor;
import org.haiku.pkg.PkgIterator;
import org.haiku.pkg.output.PkgWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStreamWriter;

/**
 * <p>This small tool will take an HPKR file and parse it first into attributes and then into packages.  The packages
 * are dumped out to the standard output.  This is useful as means of debugging.</p>
 */

public class PkgDumpTool {

    protected static Logger LOGGER = LoggerFactory.getLogger(AttributeDumpTool.class);

    @Option(name = "-f", required = true, usage = "the HPKR file is required")
    private File hpkrFile;

    public static void main(String[] args) {
        PkgDumpTool main = new PkgDumpTool();
        CmdLineParser parser = new CmdLineParser(main);

        try {
            parser.parseArgument(args);
            main.run();
        }
        catch(CmdLineException cle) {
            throw new IllegalStateException("unable to parse arguments",cle);
        }
    }

    public void run() {
        new CmdLineParser(this);

        try (HpkrFileExtractor hpkrFileExtractor = new HpkrFileExtractor(hpkrFile)) {
            OutputStreamWriter streamWriter = new OutputStreamWriter(System.out);
            PkgWriter pkgWriter = new PkgWriter(streamWriter);
            pkgWriter.write(new PkgIterator(hpkrFileExtractor.getPackageAttributesIterator()));
            pkgWriter.flush();
        } catch (Throwable th) {
            LOGGER.error("unable to dump packages", th);
        }

    }



}
