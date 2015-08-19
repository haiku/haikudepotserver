package org.haiku.haikudepotserver.pkg.model;

/**
 * <p>This object models the localization of a package version.  The values might come from
 * either the HPKG data originally or might come from per-package overrides or fallbacks to
 * english.</p>
 */

public class ResolvedPkgVersionLocalization {

    private String title;
    private String summary;
    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean hasAll() {
        return null!=title && null!=summary && null!=description;
    }

}
