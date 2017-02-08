/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * <P>This is used in the generation of meta-data for archives of data.</P>
 */

public class ArchiveInfo {

    private final static String AGENT = "hds";

    private Date createTimestamp;
    private String createTimestampIso;
    private Date dataModifiedTimestamp;
    private String dataModifiedTimestampIso;
    private String agent = AGENT;
    private String agentVersion;

    public ArchiveInfo(
            Date dataModifiedTimestamp,
            String agentVersion) {

        Date createTimestamp = new Date();
        DateTimeFormatter dateTimeFormatter = DateTimeHelper.createStandardDateTimeFormat();

        this.createTimestamp = createTimestamp;
        this.createTimestampIso = dateTimeFormatter.format(createTimestamp.toInstant());
        this.dataModifiedTimestamp = dataModifiedTimestamp;
        this.dataModifiedTimestampIso = dateTimeFormatter.format(dataModifiedTimestamp.toInstant());
        this.agentVersion = agentVersion;
    }

    public Date getCreateTimestamp() {
        return createTimestamp;
    }

    public String getCreateTimestampIso() {
        return createTimestampIso;
    }

    public Date getDataModifiedTimestamp() {
        return dataModifiedTimestamp;
    }

    public String getDataModifiedTimestampIso() {
        return dataModifiedTimestampIso;
    }

    public String getAgent() {
        return agent;
    }

    public String getAgentVersion() {
        return agentVersion;
    }
}
