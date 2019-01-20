/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.auto._PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

public class PkgSupplement extends _PkgSupplement implements MutableCreateAndModifyTimestamped {

    private static final long serialVersionUID = 1L;

    public static PkgSupplement getByBasePkgName(ObjectContext context, String basePkgName) {
        return tryGetByBasePkgName(context, basePkgName).orElseThrow(
                () -> new IllegalStateException("unable to find the pkg supplement [" + basePkgName + "]"));
    }

    public static Optional<PkgSupplement> tryGetByBasePkgName(
            ObjectContext context, String basePkgName) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(basePkgName), "the base pkg name must be supplied");
        return Optional.ofNullable(ObjectSelect.query(PkgSupplement.class).where(BASE_PKG_NAME.eq(basePkgName)).selectOne(context));
    }

    /**
     * <p>If there is a change-log then this will return it, otherwise it will return an empty optional.</p>
     */

    public Optional<PkgChangelog> getPkgChangelog() {
        return getPkgChangelogs().stream().collect(SingleCollector.optional());
    }

    public void setIconModifyTimestamp() {
        setIconModifyTimestamp(new java.sql.Timestamp(Clock.systemUTC().millis()));
    }

    public Date getLatestPkgModifyTimestampSecondAccuracy() {
        return getPkgs()
                .stream()
                .map(Pkg::getModifyTimestampSecondAccuracy)
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("there are no associated pkgs"));
    }

    public Optional<PkgIcon> getPkgIcon(final MediaType mediaType, final Integer size) {
        Preconditions.checkNotNull(mediaType);

        List<PkgIcon> icons = getPkgIcons(mediaType,size);

        switch (icons.size()) {
            case 0:
                return Optional.empty();

            case 1:
                return Optional.of(icons.get(0));

            default:
                throw new IllegalStateException("more than one pkg icon of media type "
                        + mediaType.getCode() + " of size " + size + " on pkg "
                        + getBasePkgName());
        }
    }

    private List<PkgIcon> getPkgIcons(final MediaType mediaType, final Integer size) {
        Preconditions.checkNotNull(mediaType);
        return getPkgIcons().stream().filter(pi ->
                pi.getMediaType().equals(mediaType)
                        && (null == size) == (null == pi.getSize())
                        && ((null == size) || size.equals(pi.getSize()))
        ).collect(Collectors.toList());
    }

    public List<PkgScreenshot> getSortedPkgScreenshots() {
        List<PkgScreenshot> screenshots = new ArrayList<>(getPkgScreenshots());
        Collections.sort(screenshots);
        return screenshots;
    }

    public OptionalInt getHighestPkgScreenshotOrdering() {
        return getPkgScreenshots()
                .stream()
                .mapToInt(_PkgScreenshot::getOrdering)
                .max();
    }

    /**
     * <p>This will try to find localized data for the pkg version for the supplied natural language.  Because
     * English language data is hard-coded into the package payload, english will always be available.</p>
     */

    public Optional<PkgLocalization> getPkgLocalization(final NaturalLanguage naturalLanguage) {
        return getPkgLocalization(naturalLanguage.getCode());
    }

    /**
     * <p>This will try to find localized data for the pkg version for the supplied natural language.  Because
     * English language data is hard-coded into the package payload, english will always be available.</p>
     */

    public Optional<PkgLocalization> getPkgLocalization(final String naturalLanguageCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));
        return getPkgLocalizations()
                .stream()
                .filter(pl -> pl.getNaturalLanguage().getCode().equals(naturalLanguageCode))
                .collect(SingleCollector.optional());
    }

    public Optional<PkgPkgCategory> getPkgPkgCategory(String pkgCategoryCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgCategoryCode));

        for (PkgPkgCategory pkgPkgCategory : getPkgPkgCategories()) {
            if (pkgPkgCategory.getPkgCategory().getCode().equals(pkgCategoryCode)) {
                return Optional.of(pkgPkgCategory);
            }
        }

        return Optional.empty();
    }

}
