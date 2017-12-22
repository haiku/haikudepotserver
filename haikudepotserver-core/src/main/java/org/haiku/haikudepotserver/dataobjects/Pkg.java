/*
 * Copyright 2013-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._Pkg;
import org.haiku.haikudepotserver.dataobjects.auto._PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Pkg extends _Pkg implements CreateAndModifyTimestamped {

    public static final String PATTERN_STRING_NAME_CHAR = "[^\\s/=!<>-]";

    public static final Pattern PATTERN_NAME = Pattern.compile("^" + PATTERN_STRING_NAME_CHAR + "+$");

    public static Pkg getByName(ObjectContext context, String name) {
        return tryGetByName(context, name)
                .orElseThrow(() -> new IllegalStateException("unable to find pkg for name [" + name + "]"));
    }

    public static Optional<Pkg> tryGetByName(ObjectContext context, String name) {
        Preconditions.checkArgument(null!=context, "a context must be provided to lookup a package");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "a name must be provided to get a package");

        return Optional.ofNullable(ObjectSelect
                .query(Pkg.class)
                .where(NAME.eq(name))
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.PKG.name())
                .selectOne(context));
    }


    public static List<Pkg> findByNames(ObjectContext context, Collection<String> names) {
        Preconditions.checkArgument(null!=context, "a context must be provided to lookup a package");

        if (CollectionUtils.isEmpty(names)) {
            return Collections.emptyList();
        }

        return ObjectSelect
                .query(Pkg.class)
                .where(NAME.in(names))
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.PKG.name())
                .select(context);
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if(null==getActive()) {
            setActive(true);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getName()) {
            if(!PATTERN_NAME.matcher(getName()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, NAME.getName(), "malformed"));
            }
        }

    }

    public Optional<PkgUserRatingAggregate> getPkgUserRatingAggregate(Repository repository) {
        Preconditions.checkArgument(null != repository);
        return getPkgUserRatingAggregates()
                .stream()
                .filter(pura -> pura.getRepository().equals(repository))
                .collect(SingleCollector.optional());
    }

    public Optional<PkgPkgCategory> getPkgPkgCategory(String pkgCategoryCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgCategoryCode));

        for(PkgPkgCategory pkgPkgCategory : getPkgPkgCategories()) {
            if(pkgPkgCategory.getPkgCategory().getCode().equals(pkgCategoryCode)) {
                return Optional.of(pkgPkgCategory);
            }
        }

        return Optional.empty();
    }

    public Optional<PkgIcon> getPkgIcon(final MediaType mediaType, final Integer size) {
        Preconditions.checkNotNull(mediaType);

        List<PkgIcon> icons = getPkgIcons(mediaType,size);

        switch(icons.size()) {
            case 0:
                return Optional.empty();

            case 1:
                return Optional.of(icons.get(0));

            default:
                throw new IllegalStateException("more than one pkg icon of media type "+mediaType.getCode()+" of size "+size+" on pkg "+getName());
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

    /**
     * <p>The regular {$link #getModifyTimestamp} is at millisecond accuracy.  When dealing with second-accuracy
     * dates, this can cause anomalies.  For this reason, this method will provide the modify timestamp at
     * second accuracy.</p>
     */

    public Date getModifyTimestampSecondAccuracy() {
        return DateTimeHelper.secondAccuracyDate(getModifyTimestamp());
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
     * <p>This method will re-order the screenshots according to the set of codes present in the supplied list.
     * If the same code appears twice in the list, an {@link java.lang.IllegalArgumentException} will be
     * thrown.  Any screenshots that are not mentioned in the list will be indeterminately ordered at the end
     * of the list.</p>
     */

    public void reorderPkgScreenshots(final List<String> codes) {
        Preconditions.checkNotNull(codes);

        // first check that there are no duplicates.
        if(new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalArgumentException("the codes supplied contain duplicates which would interfere with the ordering");
        }

        List<PkgScreenshot> screenshots = new ArrayList<>(getPkgScreenshots());
        screenshots.sort((o1, o2) -> {
            int o1i = codes.indexOf(o1.getCode());
            int o2i = codes.indexOf(o2.getCode());

            if (-1 == o1i && -1 == o2i) {
                return o1.getCode().compareTo(o2.getCode());
            }

            if (-1 == o1i) {
                o1i = Integer.MAX_VALUE;
            }

            if (-1 == o2i) {
                o2i = Integer.MAX_VALUE;
            }

            return Integer.compare(o1i, o2i);
        });

        for(int i=0;i<screenshots.size();i++) {
            PkgScreenshot pkgScreenshot = screenshots.get(i);
            pkgScreenshot.setOrdering(i+1);
        }
    }

    /**
     * <p>If there is a change-log then this will return it, otherwise it will return an empty optional.</p>
     */

    public Optional<PkgChangelog> getPkgChangelog() {
        return getPkgChangelogs().stream().collect(SingleCollector.optional());
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

    public UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        return builder.pathSegment(getName());
    }

    public Optional<PkgProminence> getPkgProminence(Repository repository) {
        Preconditions.checkArgument(null != repository, "the repository must be provided");
        return getPkgProminences()
                .stream()
                .filter(pp -> pp.getRepository().equals(repository))
                .collect(SingleCollector.optional());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", getName())
                .build();
    }

}