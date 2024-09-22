package org.haiku.haikudepotserver.repository.model;

import java.util.List;

/**
 * <p>This is a data structure used to capture information about a repository
 * not having been updated.</p>
 */

public class AlertRepositoryAbsentUpdateMail {

    private List<RepositorySourceAbsentUpdate> repositorySourceAbsentUpdates;

    public AlertRepositoryAbsentUpdateMail(List<RepositorySourceAbsentUpdate> repositorySourceAbsentUpdates) {
        this.repositorySourceAbsentUpdates = repositorySourceAbsentUpdates;
    }

    public List<RepositorySourceAbsentUpdate> getRepositorySourceAbsentUpdates() {
        return repositorySourceAbsentUpdates;
    }

    public void setRepositorySourceAbsentUpdates(List<RepositorySourceAbsentUpdate> repositorySourceAbsentUpdates) {
        this.repositorySourceAbsentUpdates = repositorySourceAbsentUpdates;
    }

    public static class RepositorySourceAbsentUpdate {

        private String repositorySourceCode;

        private Integer expectedUpdateFrequencyHours;

        private Integer lastUpdateAgoHours;

        public RepositorySourceAbsentUpdate(
                String repositorySourceCode,
                Integer expectedUpdateFrequencyHours,
                Integer lastUpdateAgoHours) {
            this.repositorySourceCode = repositorySourceCode;
            this.expectedUpdateFrequencyHours = expectedUpdateFrequencyHours;
            this.lastUpdateAgoHours = lastUpdateAgoHours;
        }

        public String getRepositorySourceCode() {
            return repositorySourceCode;
        }

        public void setRepositorySourceCode(String repositorySourceCode) {
            this.repositorySourceCode = repositorySourceCode;
        }

        public Integer getExpectedUpdateFrequencyHours() {
            return expectedUpdateFrequencyHours;
        }

        public void setExpectedUpdateFrequencyHours(Integer expectedUpdateFrequencyHours) {
            this.expectedUpdateFrequencyHours = expectedUpdateFrequencyHours;
        }

        public Integer getLastUpdateAgoHours() {
            return lastUpdateAgoHours;
        }

        public void setLastUpdateAgoHours(Integer lastUpdateAgoHours) {
            this.lastUpdateAgoHours = lastUpdateAgoHours;
        }
    }

}
