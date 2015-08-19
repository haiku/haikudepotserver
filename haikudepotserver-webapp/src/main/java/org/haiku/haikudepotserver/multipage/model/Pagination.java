/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.Preconditions;

import java.util.Arrays;

/**
 * <P>This object aims to provide the pagination within a list of items.  It aims to be more or less
 * like the "paginationcontroldirective.js" behaviour.</P>
 */

public class Pagination {

    private int offset;
    private int total;
    private int max;

    /**
     * @param total is the total number of items in the entire result set
     * @param offset if the offset from 0 into the total number of items
     * @param max is the maximum number of items to show on a page
     */

    public Pagination(int total, int offset, int max) {
        Preconditions.checkState(offset >= 0);
        Preconditions.checkState(total >= 0);
        Preconditions.checkState(offset < total);
        Preconditions.checkState(max >= 1);
        this.offset = offset;
        this.total = total;
        this.max = max;
    }

    // ------------------
    // ACCESSORS

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    // ------------------
    // PAGE CONTROL

    /**
     * <p>This returns the current page in the pagination based on the offset.</p>
     */

    public int getPage() {
        return (offset / max);
    }

    /**
     * <p>This returns the total number of pages in the pagination.</p>
     */

    public int getPages() {
        return (total / max) + (0 != total % max ? 1 : 0);
    }

    private int[] linearSeries(int count) {
        int[] result = new int[count];
        for(int i=0;i<count;i++) { result[i] = i; }
        return result;
    }

    private void fanFillRight(int[] result, int startI) {

        int pages = getPages() - 1;
        int page = getPage() + 1; // assume the actual page has been set already
        int len = result.length - startI;

        for (int i = 0; i < len; i++) {
            float p = (float) i / (float) (len - 1);
            float f = p * p;
            result[startI + i] = Math.max(
                    result[(startI + i) - 1] + 1,
                    page + (int) (f * (float) (pages - page)));
        }

    }

    private void fanFillLeft(int[] result, int startI) {

        int page = getPage() - 1; // assume the actual page has been set already

        for (int i = 0; i <= startI; i++) {
            float p = (float) i / (float) startI;
            float f = p * p;
            result[startI - i] = Math.min(
                    result[(startI - i) + 1] - 1,
                    page - (int) (f * (float) page));
        }

    }

    /**
     * <p>This method will return an integer array containing item offsets that can be taken to be handy
     * pages that the user might like to jump to.  This can then be used to present a list of pages within
     * a list of data.  The returned pages try to present a set of smart pages to jump to and may not be
     * strictly linear.  If there is only one page then you may be returned an empty array.</p>
     */

    public int[] generateSuggestedPages(int count) {

        Preconditions.checkState(count > 3,"the count of pages must be more than 3");

        int pages = getPages();

        if(1==pages) {
            return new int[] { 0 };
        }

        int page = getPage(); // current page.

        if(pages <= count) {
            return linearSeries(pages);
        }

        int[] result = new int[count];
        int middleI = count / 2;

        // a debugging aid to see any bad values easily.
        Arrays.fill(result,-10);

        if(page < middleI) {

            for(int i=0;i<=page;i++) {
                result[i] = i;
            }

            fanFillRight(result, page+1);

        }
        else {

            int remainder = pages - page;

            if(remainder <= (result.length - middleI) - 1) {

                for(int i=0;i<remainder;i++) {
                    result[result.length - (i + 1)] = (pages - 1) - i;
                }

                fanFillLeft(result,result.length-(remainder + 1));

            }
            else {
                result[middleI] = page;
                fanFillRight(result, middleI+1);
                fanFillLeft(result, middleI-1);
            }

        }

        return result;
    }

    // ------------------
    // STANDARD STUFF

    @Override
    public String toString() {
        return "pagination; " + offset + " in " + total;
    }

}
