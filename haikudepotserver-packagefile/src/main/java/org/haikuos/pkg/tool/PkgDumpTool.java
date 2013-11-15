/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg.tool;

import org.haikuos.pkg.HpkException;
import org.haikuos.pkg.HpkrFileExtractor;
import org.haikuos.pkg.PkgIterator;
import org.haikuos.pkg.output.PkgWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * <p>This small tool will take an HPKR file and parse it first into attributes and then into packages.  The packages
 * are dumped out to the standard output.  This is useful as means of debugging.</p>
 */

public class PkgDumpTool {

    protected static Logger logger = LoggerFactory.getLogger(AttributeDumpTool.class);

    @Option(name = "-f", required = true, usage = "the HPKR file is required")
    private File hpkrFile;

    public static void main(String[] args) throws HpkException, IOException {
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
        CmdLineParser parser = new CmdLineParser(this);

        HpkrFileExtractor hpkrFileExtractor = null;

        try {
            hpkrFileExtractor = new HpkrFileExtractor(hpkrFile);

            OutputStreamWriter streamWriter = new OutputStreamWriter(System.out);
            PkgWriter pkgWriter = new PkgWriter(streamWriter);
            pkgWriter.write(new PkgIterator(hpkrFileExtractor.getPackageAttributesIterator()));
            pkgWriter.flush();
        }
        catch(Throwable th) {
            logger.error("unable to dump packages",th);
        }
        finally {
            if(null!=hpkrFileExtractor) {
                hpkrFileExtractor.close();
            }
        }

    }



}
