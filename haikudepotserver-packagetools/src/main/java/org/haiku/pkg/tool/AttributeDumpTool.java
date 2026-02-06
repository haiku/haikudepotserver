/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.tool;

import org.haiku.pkg.FileHelper;
import org.haiku.pkg.HpkgFileExtractor;
import org.haiku.pkg.HpkrFileExtractor;
import org.haiku.pkg.model.FileType;
import org.haiku.pkg.output.AttributeWriter;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * <p>Given an HPKR/HPKG file, this small program will dump all of the attributes
 * of the HPKR/HPKG file.  This is handy for diagnostic purposes.</p>
 */

public class AttributeDumpTool implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeDumpTool.class);

    @Option(name = "-f", required = true, usage = "the HPKR/HPKG file is required")
    private File hpkFile;

    static void main(String[] args) {
        AttributeDumpTool main = new AttributeDumpTool();
        CmdLineParser parser = new CmdLineParser(main);

        try {
            parser.parseArgument(args);
            main.run();
        }
        catch (CmdLineException cle) {
            throw new IllegalStateException("unable to parse arguments",cle);
        }
        catch (Throwable th) {
            LOGGER.error("failure in attribute dump tool", th);
        }
    }

    public void run() {
        try {
            switch (getType()) {
                case HPKG -> runHpkg();
                case HPKR -> runHpkr();
                default -> throw new IllegalStateException("unknown file format");
            }
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private void runHpkr() throws IOException {
        try (
                HpkrFileExtractor hpkrFileExtractor = new HpkrFileExtractor(hpkFile);
                OutputStreamWriter streamWriter = new OutputStreamWriter(System.out);
                AttributeWriter attributeWriter = new AttributeWriter(streamWriter) ) {
            attributeWriter.write(hpkrFileExtractor.getPackageAttributesIterator());
            attributeWriter.flush();
        }
    }

    private void runHpkg() throws IOException {
        try (
                HpkgFileExtractor hpkgFileExtractor = new HpkgFileExtractor(hpkFile);
                OutputStreamWriter streamWriter = new OutputStreamWriter(System.out);
                AttributeWriter attributeWriter = new AttributeWriter(streamWriter) ) {
            writeHeader(streamWriter,"package attributes");
            attributeWriter.write(hpkgFileExtractor.getPackageAttributesIterator());
            writeHeader(streamWriter,"toc");
            attributeWriter.write(hpkgFileExtractor.getTocIterator());
            attributeWriter.flush();
        }
    }

    private void writeHeader(Writer writer, String headline) throws IOException {
        writer.write(headline);
        writer.write(":\n");
        writer.write("-------------------\n");
        writer.flush();
    }

    private FileType getType() {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(hpkFile, "r")) {
            FileHelper fileHelper = new FileHelper();
            return fileHelper.getType(randomAccessFile);
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

}
