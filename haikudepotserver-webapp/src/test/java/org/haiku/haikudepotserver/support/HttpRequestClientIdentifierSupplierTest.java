/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import org.fest.assertions.Assertions;
import org.junit.Test;

public class HttpRequestClientIdentifierSupplierTest {

    @Test
    public void testObfuscate_ipv4() {
        // GIVEN
        String input = "1.2.3.4";

        // WHEN
        String output = HttpRequestClientIdentifierSupplier.toObfuscated(input);

        // THEN
        Assertions.assertThat(output).endsWith(".4");
    }

    @Test
    public void testObfuscate_notIpv4() {
        // GIVEN
        String input = "How Now Brown Cow.6";

        // WHEN
        String output = HttpRequestClientIdentifierSupplier.toObfuscated(input);

        // THEN
        Assertions.assertThat(output).matches("^[0-9a-f]+$");
    }

}