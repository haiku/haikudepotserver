package org.haiku.haikudepotserver.api1.model.repository;

import java.util.List;

public class UpdateRepositorySourceMirrorRequest {

    public enum Filter {
        ACTIVE,
        BASE_URL,
        DESCRIPTION,
        IS_PRIMARY,
        COUNTRY
    }

    public String code;

    public String baseUrl;

    public String countryCode;

    public String description;

    public Boolean isPrimary;

    public Boolean active;

    public List<UpdateRepositorySourceMirrorRequest.Filter> filter;

}
