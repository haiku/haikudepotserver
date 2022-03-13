/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.support.model.Contributor;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ContributorsServiceTest {

    @Test
    public void testContributors() {
        // GIVEN
        ContributorsService service = new ContributorsService();

        // WHEN
        List<Contributor> contributors = service.getConstributors();

        // THEN
        // check a couple of spot cases.
        Assertions.assertThat(contributors).contains(
                new Contributor(Contributor.Type.ENGINEERING, "Andrew Lindesay"),
                new Contributor(Contributor.Type.LOCALIZATION, "Humdinger", "de")
        );
    }

}
