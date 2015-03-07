/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.search;

import com.google.common.base.Strings;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.support.LikeHelper;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;

/**
 * <p>This class contains simple static methods that can be used to help assemble SQL queries
 * that are then used to search for package versions.  This is used in the
 * {@link org.haikuos.haikudepotserver.pkg.search.PkgSearchQuery} as well as in the
 * {@link org.haikuos.haikudepotserver.pkg.PkgOrchestrationService}.</p>
 */

public class PkgSearchHelper {

    public static void appendSqlEnglishNaturalLanguageId(Appendable sql) throws IOException {
        sql.append("(SELECT id FROM haikudepot.natural_language nlx WHERE code='");
        sql.append(NaturalLanguage.CODE_ENGLISH);
        sql.append("')");
    }

    public static void appendSqlInClause(Appendable sql, int items) throws IOException {
        sql.append(" IN (");

        for(int i=0;i<items;i++) {
            if(0!=i) {
                sql.append(',');
            }

            sql.append('?');
        }

        sql.append(')');
    }

    public static void appendSqlSearchFromClause(
            Appendable sql,
            List<Object> parameterAccumulator,
            PkgSearchSpecification searchSpecification) throws IOException {

        sql.append("haikudepot.pkg_version pv\n");
        sql.append("JOIN haikudepot.pkg p ON p.id = pv.pkg_id\n");

        if(null!=searchSpecification.getArchitectures() && !searchSpecification.getArchitectures().isEmpty()) {
            sql.append("JOIN haikudepot.architecture a ON a.id = pv.architecture_id\n");
        }

        {
            sql.append("LEFT JOIN haikudepot.pkg_localization pl ON pl.pkg_id = p.id\n");

            if(null!=searchSpecification.getNaturalLanguage()) {
                sql.append("AND (pl.natural_language_id=?");
                parameterAccumulator.add(searchSpecification.getNaturalLanguage().getObjectId().getIdSnapshot().get("id"));

                if (!searchSpecification.getNaturalLanguage().getCode().equals(NaturalLanguage.CODE_ENGLISH)) {
                    sql.append(" OR\n");
                    sql.append("(pl.natural_language_id=");
                    appendSqlEnglishNaturalLanguageId(sql);
                    sql.append(" AND NOT EXISTS(SELECT plx.id FROM haikudepot.pkg_localization plx WHERE plx.pkg_id=p.id AND plx.natural_language_id = ?))\n");
                    parameterAccumulator.add(searchSpecification.getNaturalLanguage().getObjectId().getIdSnapshot().get("id"));
                }

                sql.append(')');
            }
            else {
                sql.append("AND pl.natural_language_id=");
                appendSqlEnglishNaturalLanguageId(sql);
            }

            sql.append('\n');
        }

        if(null != searchSpecification.getPkgCategory()) {
            sql.append("JOIN haikudepot.pkg_pkg_category ppc ON ppc.pkg_id = p.id\n");
        }

        if(!Strings.isNullOrEmpty(searchSpecification.getExpression())) {
            sql.append("LEFT JOIN haikudepot.pkg_version_localization pvl ON pvl.pkg_version_id = pv.id\n");

            if(null!=searchSpecification.getNaturalLanguage()) {
                sql.append("AND (pvl.natural_language_id=?");
                parameterAccumulator.add(searchSpecification.getNaturalLanguage().getObjectId().getIdSnapshot().get("id"));

                if (!searchSpecification.getNaturalLanguage().getCode().equals(NaturalLanguage.CODE_ENGLISH)) {
                    sql.append(" OR\n");
                    sql.append("(pvl.natural_language_id=");
                    appendSqlEnglishNaturalLanguageId(sql);
                    sql.append("\nAND NOT EXISTS(SELECT pvlx.id FROM haikudepot.pkg_version_localization pvlx WHERE pvlx.pkg_version_id=pv.id AND pvlx.natural_language_id = ?))\n");
                    parameterAccumulator.add(searchSpecification.getNaturalLanguage().getObjectId().getIdSnapshot().get("id"));
                }

                sql.append(')');
            }
            else {
                sql.append("AND pvl.natural_language_id=?");
                appendSqlEnglishNaturalLanguageId(sql);
            }

            sql.append('\n');
        }

        if(searchSpecification.getSortOrdering() == PkgSearchSpecification.SortOrdering.PROMINENCE) {
            sql.append("LEFT JOIN haikudepot.prominence pr ON p.prominence_id = pr.id\n");
        }

    }

    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    public static void appendSqlSearchWhereClause(
            Appendable sql,
            List<Object> parameterAccumulator,
            PkgSearchSpecification search) throws IOException {

        sql.append("pv.is_latest = true\n");

        if(null!=search.getArchitectures() && !search.getArchitectures().isEmpty()) {
            sql.append("AND a.id");
            appendSqlInClause(sql, search.getArchitectures().size());

            for(Architecture architecture : search.getArchitectures()) {
                parameterAccumulator.add(architecture.getObjectId().getIdSnapshot().get("id"));
            }

            sql.append("\n");
        }

        if(!search.getIncludeInactive()) {
            sql.append("AND p.active = true\n");
            sql.append("AND pv.active = true\n");
        }

        if(null != search.getDaysSinceLatestVersion()) {
            sql.append("AND pc.create_timestamp >= ?\n");
            parameterAccumulator.add(new java.sql.Timestamp(DateTime.now().minusDays(search.getDaysSinceLatestVersion().intValue()).getMillis()));
        }

        if(!Strings.isNullOrEmpty(search.getExpression())) {

            sql.append("AND (\n");
            sql.append("LOWER(pvl.summary) LIKE ? ESCAPE '"+ LikeHelper.CHAR_ESCAPE+"'\n");
            sql.append("OR LOWER(p.name) LIKE ? ESCAPE '"+ LikeHelper.CHAR_ESCAPE+"'\n");
            sql.append("OR LOWER(pl.title) LIKE ? ESCAPE '"+ LikeHelper.CHAR_ESCAPE+"'\n");
            sql.append(")\n");

            String sqlExpr;

            switch(search.getExpressionType()) {

                case CONTAINS:
                    sqlExpr = '%' + LikeHelper.ESCAPER.escape(search.getExpression().toLowerCase()) + '%';
                    break;

                default:
                    throw new IllegalStateException("unknown search expression type");

            }

            parameterAccumulator.add(sqlExpr);
            parameterAccumulator.add(sqlExpr);
            parameterAccumulator.add(sqlExpr);
        }

        if(null != search.getPkgCategory()) {
            sql.append("AND ppc.pkg_category_id = ?\n");
            parameterAccumulator.add(search.getPkgCategory().getObjectId().getIdSnapshot().get("id"));
        }

        if(null != search.getPkgNames()) {

            if(search.getPkgNames().isEmpty()) {
                sql.append("AND true = false\n");
            }
            else {
                sql.append("AND p.name");
                appendSqlInClause(sql, search.getPkgNames().size());

                for(String pkgName : search.getPkgNames()) {
                    parameterAccumulator.add(pkgName);
                }
            }

        }
    }

    public static void appendSearchOrderClause(
            Appendable sql,
            PkgSearchSpecification search) throws IOException {

        if(null!=search.getSortOrdering()) {

            switch (search.getSortOrdering()) {

                case VERSIONVIEWCOUNTER:
                    sql.append("pv.view_counter DESC,\n");
                    break;

                case VERSIONCREATETIMESTAMP:
                    sql.append("pv.create_timestamp DESC,\n");
                    break;

                case PROMINENCE:
                    sql.append("pr.ordering ASC,\n");
                    break;

                case NAME: // gets added anyway...
                    break;

                default:
                    throw new IllegalStateException("unhandled sort ordering; " + search.getSortOrdering());

            }
        }

        sql.append("LOWER(COALESCE(pl.title,p.name)) ASC\n");
    }

}
