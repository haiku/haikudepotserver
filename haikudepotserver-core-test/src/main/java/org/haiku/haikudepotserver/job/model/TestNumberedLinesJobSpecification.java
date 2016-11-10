/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

public class TestNumberedLinesJobSpecification extends AbstractJobSpecification {

    private int lines = 1;
    private long delayPerLineMillis = 0;

    public TestNumberedLinesJobSpecification(int lines, long delayPerLineMillis) {
        this.lines = lines;
        this.delayPerLineMillis = delayPerLineMillis;
    }

    public int getLines() {
        return lines;
    }

    public void setLines(int lines) {
        this.lines = lines;
    }

    public long getDelayPerLineMillis() {
        return delayPerLineMillis;
    }

    public void setDelayPerLineMillis(long delayPerLineMillis) {
        this.delayPerLineMillis = delayPerLineMillis;
    }

}
