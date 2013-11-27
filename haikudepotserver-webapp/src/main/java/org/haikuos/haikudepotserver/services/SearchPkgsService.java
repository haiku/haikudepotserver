/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.services;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ejbql.parser.EJBQL;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.model.Architecture;
import org.haikuos.haikudepotserver.model.Pkg;
import org.haikuos.haikudepotserver.model.PkgVersion;
import org.haikuos.haikudepotserver.services.model.SearchPkgsSpecification;
import org.haikuos.haikudepotserver.services.model.SearchPkgsSpecification;
import org.haikuos.haikudepotserver.support.cayenne.LikeHelper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * <p>This service affords the ability for clients to search for packages in a standardized manner.</p>
 */

@Service
public class SearchPkgsService {

    public List<Pkg> search(ObjectContext context, SearchPkgsSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);
        Preconditions.checkNotNull(search.getArchitectures());
        Preconditions.checkState(!search.getArchitectures().isEmpty());

        List<String> pkgNames;

        // using jpql because of need to get out raw rows for the pkg name.

        {
            StringBuilder queryBuilder = new StringBuilder();
            List<Object> parameters = Lists.newArrayList();
            List<Architecture> architecturesList = Lists.newArrayList(search.getArchitectures());

            queryBuilder.append("SELECT DISTINCT pv.pkg.name FROM PkgVersion pv WHERE");

            queryBuilder.append(" (");

            for(int i=0; i < architecturesList.size(); i++) {
                if(0!=i) {
                    queryBuilder.append(" OR");
                }

                queryBuilder.append(String.format(" pv.architecture.code = ?%d",parameters.size()+1));
                parameters.add(architecturesList.get(i).getCode());
            }

            queryBuilder.append(")");

            queryBuilder.append(" AND");
            queryBuilder.append(" pv.active = true");
            queryBuilder.append(" AND");
            queryBuilder.append(" pv.pkg.active = true");

            if(!Strings.isNullOrEmpty(search.getExpression())) {
                queryBuilder.append(" AND");
                queryBuilder.append(String.format(" pv.pkg.name LIKE ?%d",parameters.size()+1));
                parameters.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
            }

            queryBuilder.append(" ORDER BY pv.pkg.name ASC");

            EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

            for(int i=0;i<parameters.size();i++) {
                query.setParameter(i+1,parameters.get(i));
            }

            // [apl 13.nov.2013]
            // There seems to be a problem with the resolution of "IN" parameters; it doesn't seem to handle
            // the collection in the parameter very well.  See EJBQLConditionTranslator.processParameter(..)
            // Seems to be a problem if it is a data object or a scalar.

//            queryBuilder.append("SELECT DISTINCT pv.pkg.name FROM PkgVersion pv WHERE");
//            //queryBuilder.append(" pv.architecture IN (:architectures)");
//            queryBuilder.append(" pv.architecture.code IN (:architectureCodes)");
//            queryBuilder.append(" AND");
//            queryBuilder.append(" pv.active=true");
//            queryBuilder.append(" AND");
//            queryBuilder.append(" pv.pkg.active=true");
//
//            if(!Strings.isNullOrEmpty(search.getExpression())) {
//                queryBuilder.append(" AND");
//                queryBuilder.append(" pv.pkg.name LIKE :pkgNameLikeExpression");
//            }
//
//            queryBuilder.append(" ORDER BY pv.pkg.name ASC");
//
//            EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());
//
//            //query.setParameter("architectures", search.getArchitectures());
//            query.setParameter("architectureCodes", Iterables.transform(
//                    search.getArchitectures(),
//                    new Function<Architecture, String>() {
//                        @Override
//                        public String apply(Architecture architecture) {
//                            return architecture.getCode();
//                        }
//                    }
//            ));
//
//            if(!Strings.isNullOrEmpty(search.getExpression())) {
//                query.setParameter("pkgNameLikeExpression", "%" + LikeHelper.escapeExpression(search.getExpression()) + "%");
//            }

            query.setFetchOffset(search.getOffset());
            query.setFetchLimit(search.getLimit());

            pkgNames = (List<String>) context.performQuery(query);
        }

        List<Pkg> pkgs = Collections.emptyList();

        if(0!=pkgNames.size()) {
            SelectQuery query = new SelectQuery(Pkg.class, ExpressionFactory.inExp(Pkg.NAME_PROPERTY, pkgNames));
            pkgs = context.performQuery(query);
        }

        return pkgs;
    }

}
