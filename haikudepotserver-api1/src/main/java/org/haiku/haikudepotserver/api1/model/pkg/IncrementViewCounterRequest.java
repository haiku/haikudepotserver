package org.haiku.haikudepotserver.api1.model.pkg;

public class IncrementViewCounterRequest {

    public String architectureCode;

    public String repositoryCode;

    public String name;

    // version coordinates.

    public String major;

    public String minor;

    public String micro;

    public String preRelease;

    public Integer revision;
}
