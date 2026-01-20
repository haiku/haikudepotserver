package org.haiku.haikudepotserver.support.progress;

import org.fest.assertions.Assertions;
import org.junit.Test;

import java.util.List;

public class CompositeProgressImplTest {

    @Test
    public void testEmpty() {
        //GIVEN
        CompositeProgressImpl composite = new CompositeProgressImpl(List.of());

        //WHEN
        int result = composite.percentage();

        // THEN
        Assertions.assertThat(result).isEqualTo(100);
    }

    @Test
    public void testSingle() {
        // GIVEN
        CompositeProgressImpl composite = new CompositeProgressImpl(
                List.of(new WeightedProgressImpl(123, new SimpleProgressImpl(50))));

        // WHEN
        int result = composite.percentage();

        // THEN
        Assertions.assertThat(result).isEqualTo(50);
    }

    @Test
    public void testMultiple() {
        // GIVEN
        CompositeProgressImpl composite = new CompositeProgressImpl(
                List.of(
                        new WeightedProgressImpl(100, new SimpleProgressImpl(100)),
                        new WeightedProgressImpl(200, new SimpleProgressImpl(25)),
                        new WeightedProgressImpl(100, new SimpleProgressImpl())
                )
        );

        // WHEN
        int result = composite.percentage();

        // THEN
        Assertions.assertThat(result).isEqualTo(38);
    }

}
