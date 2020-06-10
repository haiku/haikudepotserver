package org.haiku.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GetAllContributorsResult {

    private final List<Contributor> contributors;

    public GetAllContributorsResult(List<Contributor> contributors) {
        this.contributors = contributors;
    }

    public List<Contributor> getContributors() {
        return contributors;
    }

    public static class Contributor {

        public enum Type {
            ENGINEERING,
            LOCALIZATION
        }

        private final Type type;
        private final String name;
        private final String naturalLanguageCode;

        public Contributor(Type type, String name, String naturalLanguageCode) {
            this.type = type;
            this.name = name;
            this.naturalLanguageCode = naturalLanguageCode;
        }

        public Type getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getNaturalLanguageCode() {
            return naturalLanguageCode;
        }
    }

}
