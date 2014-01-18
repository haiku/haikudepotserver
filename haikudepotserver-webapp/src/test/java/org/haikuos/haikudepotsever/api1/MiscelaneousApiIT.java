/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.api1;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.MiscellaneousApi;
import org.haikuos.haikudepotserver.api1.model.miscellaneous.GetAllArchitecturesRequest;
import org.haikuos.haikudepotserver.api1.model.miscellaneous.GetAllArchitecturesResult;
import org.haikuos.haikudepotserver.api1.model.miscellaneous.GetAllMessagesRequest;
import org.haikuos.haikudepotserver.api1.model.miscellaneous.GetAllMessagesResult;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.junit.Test;

import javax.annotation.Resource;

public class MiscelaneousApiIT extends AbstractIntegrationTest {

    @Resource
    MiscellaneousApi miscellaneousApi;

    @Test
    public void testGetAllMessages() {

        // ------------------------------------
        GetAllMessagesResult result = miscellaneousApi.getAllMessages(new GetAllMessagesRequest());
        // ------------------------------------

        Assertions.assertThat(result.messages.get("test.it")).isEqualTo("Test line for integration testing");

    }

    private Optional<GetAllArchitecturesResult.Architecture> isPresent(
            GetAllArchitecturesResult result,
            final String architectureCode) {
        return Iterables.tryFind(result.architectures, new Predicate<GetAllArchitecturesResult.Architecture>() {
            @Override
            public boolean apply(GetAllArchitecturesResult.Architecture architecture) {
                return architecture.code.equals(architectureCode);
            }
        });
    }

    @Test
    public void testGetAllArchitectures() {

        // ------------------------------------
        GetAllArchitecturesResult result = miscellaneousApi.getAllArchitectures(new GetAllArchitecturesRequest());
        // ------------------------------------

        // not sure what architectures there may be in the future, but
        // we will just check for a couple that we know to be there.

        Assertions.assertThat(isPresent(result,"x86").isPresent()).isTrue();
        Assertions.assertThat(isPresent(result,"x86_gcc2").isPresent()).isTrue();
        Assertions.assertThat(isPresent(result,"mips").isPresent()).isFalse();
    }

}
