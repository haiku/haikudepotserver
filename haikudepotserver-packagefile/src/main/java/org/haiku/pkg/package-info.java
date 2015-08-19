/**
 * <p>This package contains controller, model and helper objects for reading package files for the
 * <a href="http://www.haiku-os.org">Haiku</a> project.  Pkg files come in two types.  HPKR is a file
 * format for providing a kind of catalogue of what is in a repository.  HPKG format is a file that describes
 * a particular package.  At the time of writing, this library only supports HPKR although there is enough
 * supporting material to easily provide a reader for HPKG.</p>
 *
 * <p>Note that this library (currently) only supports (signed) 32bit addressing in the HPKR files.</p>
 */

package org.haiku.pkg;