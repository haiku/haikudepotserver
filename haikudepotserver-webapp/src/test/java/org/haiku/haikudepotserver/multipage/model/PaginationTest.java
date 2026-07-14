/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.model;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PaginationTest {

    @Test
    public void testHasNextPage_emptyFalse() {
        // GIVEN
        Pagination p = new Pagination(0, 0, 10);

        // WHEN
        boolean actual = p.hasNextPage();

        // THEN
        Assertions.assertThat(actual).isFalse();
    }

    @Test
    public void testHasNextPage_nonEmptyFalse() {
        // GIVEN
        Pagination p = new Pagination(15, // <-- the pagination is at the end
                20, 10);

        // WHEN
        boolean actual = p.hasNextPage();

        // THEN
        Assertions.assertThat(actual).isFalse();
    }

    @Test
    public void testHasNextPage_nonEmptyTrue() {
        // GIVEN
        Pagination p = new Pagination(2, // <-- the pagination is at the start so has a next page
                20, 10);

        // WHEN
        boolean actual = p.hasNextPage();

        // THEN
        Assertions.assertThat(actual).isTrue();
    }

    @Test
    public void testHasPreviousPage_emptyFalse() {
        // GIVEN
        Pagination p = new Pagination(0, 0, 10);

        // WHEN
        boolean actual = p.hasPreviousPage();

        // THEN
        Assertions.assertThat(actual).isFalse();
    }

    @Test
    public void testHasPreviousPage_nonEmptyFalse() {
        // GIVEN
        Pagination p = new Pagination(5, // <-- the pagination is at the start
                20, 10);

        // WHEN
        boolean actual = p.hasPreviousPage();

        // THEN
        Assertions.assertThat(actual).isFalse();
    }

    @Test
    public void testHasPreviousPage_nonEmptyTrue() {
        // GIVEN
        Pagination p = new Pagination(18, // <-- the pagination is at the end
                20, 10);

        // WHEN
        boolean actual = p.hasPreviousPage();

        // THEN
        Assertions.assertThat(actual).isTrue();
    }

    @Test
    public void testGenerateSuggestedPages_none() {
        // GIVEN
        Pagination p = new Pagination(0, 0, 10);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(6);

        // THEN
        Assertions.assertThat(actual).isEmpty();
    }

    @Test
    public void testGenerateSuggestedPages_one() {
        // GIVEN
        Pagination p = new Pagination(4, 5, 10);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(6);

        // THEN
        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, true)
        );
    }

    @Test
    public void testGenerateSuggestedPages_linearLessThanSuggested() {
        // GIVEN
        Pagination p = new Pagination(6, 16, 4);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(6);

        // THEN
        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, false),
                new Page(1, 4, true),
                new Page(2, 8, false),
                new Page(3, 12, false)
        );
    }

    @Test
    public void testGenerateSuggestedPages_linearEqualToSuggested() {
        // GIVEN
        Pagination p = new Pagination(6, 16, 4);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(4);

        // THEN
        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, false),
                new Page(1, 4, true),
                new Page(2, 8, false),
                new Page(3, 12, false)
        );
    }

    /**
     * <p>The current page is in the middle of a large number of pages.</p>
     */

    @Test
    public void testGenerateSuggestedPages_middleSpread() {
        // GIVEN
        Pagination p = new Pagination(24, 64, 4);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(7);

        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, false),
                new Page(4, 16, false),
                new Page(5, 20, false),
                new Page(6, 24, true),
                new Page(7, 28, false),
                new Page(8, 32, false),
                new Page(15, 60, false)
        );
    }

    /**
     * <p>The current page is to the left of the pages.</p>
     */

    @Test
    public void testGenerateSuggestedPages_leftLeaningSpread() {
        // GIVEN
        Pagination p = new Pagination(5, 64, 4);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(7);

        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, false),
                new Page(1, 4, true),
                new Page(2, 8, false),
                new Page(3, 12, false),
                new Page(4, 16, false),
                new Page(5, 20, false),
                new Page(15, 60, false)
        );
    }

    /**
     * <p>The current page is to the left of the pages.</p>
     */

    @Test
    public void testGenerateSuggestedPages_rightLeaningSpread() {
        // GIVEN
        Pagination p = new Pagination(51, 64, 4);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(7);

        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, false),
                new Page(10, 40, false),
                new Page(11, 44, false),
                new Page(12, 48, true),
                new Page(13, 52, false),
                new Page(14, 56, false),
                new Page(15, 60, false)
        );
    }

    @Test
    public void testGenerateSuggestedPages_rightLeaningSpreadLarge() {
        // GIVEN
        Pagination p = new Pagination(4230, 4246, 15);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(5);

        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, false),
                new Page(280, 4200, false),
                new Page(281, 4215, false),
                new Page(282, 4230, true),
                new Page(283, 4245, false)
        );
    }

    @Test
    public void testGenerateSuggestedPages_rightLeaningSpreadLargeAtEnd() {
        // GIVEN
        Pagination p = new Pagination(4245, 4246, 15);

        // WHEN
        List<Page> actual = p.generateSuggestedPages(5);

        Assertions.assertThat(actual).containsExactly(
                new Page(0, 0, false),
                new Page(280, 4200, false),
                new Page(281, 4215, false),
                new Page(282, 4230, false),
                new Page(283, 4245, true)
        );
    }

}
