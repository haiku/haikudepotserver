/**
 * <p>This package contains a "search" mechanism for package versions.  This was originally
 * coded using EJBQL (Cayenne ORM), but it proved too difficult to introduce ordering on
 * packages that had a mix of titles and no titles; hence it was moved to SQL.</p>
 *
 * <p>This package contains most of the SQL logic for the search.  Raw SQL was used rather
 * than using the Cayenne velocity template technique because I want to use the exact same
 * SQL to pull in the package versions as is used to get the total count of the package
 * versions.</p>
 */

package org.haikuos.haikudepotserver.pkg.search;