/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.tool;

import com.google.common.io.Files;
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

    protected final static Logger LOGGER = LoggerFactory.getLogger(PkgDumpTool.class);

    @Option(name = "-f", required = true, usage = "the HPKR/HPKG file is required")
    private File file;

    static void main(String[] args) {
        PkgDumpTool main = new PkgDumpTool();
        CmdLineParser parser = new CmdLineParser(main);

        try {
            parser.parseArgument(args);
            main.run();
        } catch (CmdLineException cle) {
            throw new IllegalStateException("unable to parse arguments", cle);
        }
    }

    public void run() {
        new CmdLineParser(this);

        String fileExtension = Files.getFileExtension(file.getName());

        switch (fileExtension.toLowerCase()) {
            case "hpkr":
                LOGGER.info("will read from HPKR [{}]", file);
                try (
                        HpkrFileExtractor hpkrFileExtractor = new HpkrFileExtractor(file);
                        OutputStreamWriter streamWriter = new OutputStreamWriter(System.out);
                        PkgWriter pkgWriter = new PkgWriter(streamWriter)) {
                    pkgWriter.write(new PkgIterator(hpkrFileExtractor.getPackageAttributesIterator()));
                    pkgWriter.flush();
                } catch (Throwable th) {
                    LOGGER.error("unable to dump packages", th);
                }
                break;

            case "hpkg":
                throw new IllegalStateException("this tool does not work with HPKG files");
            default:
                throw new IllegalStateException("unsupported file extension [%s]".formatted(fileExtension));
        }
    }



}
