package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgLocalization;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

import java.util.Collections;
import java.util.List;

public class PkgLocalization extends _PkgLocalization implements CreateAndModifyTimestamped {

    public static List<PkgLocalization> findForPkgs(ObjectContext context, List<Pkg> pkgs) {
        Preconditions.checkArgument(null!=context, "the context must be supplied");
        Preconditions.checkArgument(null!=pkgs, "the pkgs must be supplied");
        assert null!=pkgs;

        if(pkgs.isEmpty()) {
            return Collections.emptyList();
        }

        SelectQuery query = new SelectQuery(
                PkgLocalization.class,
                ExpressionFactory.inExp(PkgLocalization.PKG_PROPERTY, pkgs));

        return (List<PkgLocalization>) context.performQuery(query);
    }

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
        return Iterables.tryFind(
                findForPkg(context, pkg),
                new Predicate<PkgLocalization>() {
                    @Override
                    public boolean apply(PkgLocalization input) {
                        return input.getNaturalLanguage().getCode().equals(naturalLanguageCode);
                    }
                }
        );
    }

}
