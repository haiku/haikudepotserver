/*
 * Copyright 2016-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PkgLocalization;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PkgLocalization extends _PkgLocalization implements MutableCreateAndModifyTimestamped {

    public static List<PkgLocalization> findForPkg(ObjectContext context, Pkg pkg) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");
        return ObjectSelect
                .query(PkgLocalization.class)
                .where(PKG.eq(pkg))
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.PKG_LOCALIZATION.name())
                .select(context);
    }

    public static Optional<PkgLocalization> getForPkgAndNaturalLanguageCode(ObjectContext context, Pkg pkg, final String naturalLanguageCode) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(naturalLanguageCode), "the natural language code must be supplied");
        return findForPkg(context, pkg).stream().filter(l -> l.getNaturalLanguage().getCode().equals(naturalLanguageCode)).collect(SingleCollector.optional());
    }

    // TODO; this is not so good because it mutates the data when validating.

    @Override
    protected void validateForSave(ValidationResult validationResult) {

        String titleTrimmed = Strings.emptyToNull(Strings.nullToEmpty(getTitle()).trim());
        String descriptionTrimmed = Strings.emptyToNull(Strings.nullToEmpty(getDescription()).trim());
        String summaryTrimmed = Strings.emptyToNull(Strings.nullToEmpty(getSummary()).trim());

        if(!Objects.equals(titleTrimmed, getTitle())) {
           setTitle(titleTrimmed);
        }

        if(!Objects.equals(descriptionTrimmed, getDescription())) {
            setDescription(descriptionTrimmed);
        }

        if(!Objects.equals(summaryTrimmed, getSummary())) {
            setSummary(summaryTrimmed);
        }

        super.validateForSave(validationResult);

    }

}
