/*
 * Copyright 2015-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.singlepage;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.fest.assertions.Assertions.assertThat;

public class SinglePageTemplateFrequencyMetricsTest {

    @Test
    public void typicalFlow() {
        SinglePageTemplateFrequencyMetrics metrics = new SinglePageTemplateFrequencyMetrics();
        metrics.increment("/page4.html");
        metrics.increment("/page1.html");
        metrics.increment("/page3.html");
        metrics.increment("/page1.html");
        metrics.increment("/page2.html");
        metrics.increment("/page3.html");
        metrics.increment("/page3.html");
        metrics.increment("/page3.html");
        metrics.increment("/page2.html");

        assertThat(metrics.top(3)).isEqualTo(ImmutableList.of(
                "/page3.html", "/page1.html", "/page2.html"
        ));

    }

}
