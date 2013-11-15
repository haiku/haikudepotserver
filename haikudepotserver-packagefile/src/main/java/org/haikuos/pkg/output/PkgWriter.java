/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg.output;

import com.google.common.base.Preconditions;
import org.haikuos.pkg.HpkException;
import org.haikuos.pkg.PkgException;
import org.haikuos.pkg.PkgIterator;
import org.haikuos.pkg.model.Pkg;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * <p>This is a writer in the sense of being able to produce a human-readable output of a package.</p>
 */

public class PkgWriter extends FilterWriter {

    public PkgWriter(Writer writer) {
        super(writer);
    }

    private void write(Pkg pkg) throws IOException {
        Preconditions.checkNotNull(pkg);
        write(pkg.toString());
    }

    public void write(PkgIterator pkgIterator) throws IOException, HpkException {
        Preconditions.checkNotNull(pkgIterator);

        try {
            while(pkgIterator.hasNext()) {
                write(pkgIterator.next());
                write('\n');
            }
        }
        catch(PkgException pe) {
            throw new IOException("unable to write a package owing to an exception obtaining the package",pe);
        }
    }

}
