package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.haiku.haikudepotserver.dataobjects.auto._PkgLocalization;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class PkgLocalization extends _PkgLocalization implements CreateAndModifyTimestamped {

    public static List<PkgLocalization> findForPkg(ObjectContext context, Pkg pkg) {
        Preconditions.checkArgument(null!=context, "the context must be supplied");
        Preconditions.checkArgument(null!=pkg, "the pkg must be supplied");

        SelectQuery query = new SelectQuery(
                PkgLocalization.class,
                ExpressionFactory.matchExp(PkgLocalization.PKG_PROPERTY, pkg));

        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.setCacheGroups(HaikuDepot.CacheGroup.PKG_LOCALIZATION.name());

        return (List<PkgLocalization>) context.performQuery(query);
    }

    public static Optional<PkgLocalization> getForPkgAndNaturalLanguageCode(ObjectContext context, Pkg pkg, final String naturalLanguageCode) {
        Preconditions.checkArgument(null!=context, "the context must be supplied");
        Preconditions.checkArgument(null!=pkg, "the pkg must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(naturalLanguageCode), "the natural language code must be supplied");
        return findForPkg(context, pkg).stream().filter(l -> l.getNaturalLanguage().getCode().equals(naturalLanguageCode)).collect(SingleCollector.optional());
    }

}
