/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.Permission;
import org.haikuos.haikudepotserver.dataobjects.PermissionUserPkg;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.model.AuthorizationPkgRule;
import org.haikuos.haikudepotserver.security.model.AuthorizationPkgRuleSearchSpecification;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <p>This service is designed to orchestrate the authorization rules in the system.</p>
 */

@Service
public class AuthorizationPkgRuleOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(AuthorizationPkgRuleOrchestrationService.class);

    private static int LIMIT_GENERATECSV = 100;

    @SuppressWarnings("UnusedParameters")
    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            AuthorizationPkgRuleSearchSpecification specification) {

        List<String> clauses = new ArrayList<>();

        if(null!= specification.getPermissions()) {

            StringBuilder query = new StringBuilder();
            query.append('(');

            for(int i=0;i< specification.getPermissions().size();i++) {
                if(0!=i) {
                    query.append(" OR ");
                }

                parameterAccumulator.add(specification.getPermissions().get(i));
                query.append("r.permission=?");
                query.append(Integer.toString(parameterAccumulator.size()));
            }

            query.append(')');

            clauses.add(query.toString());
        }

        if(null!= specification.getUser()) {
            parameterAccumulator.add(specification.getUser());
            clauses.add("r.user=?"+parameterAccumulator.size());

            if(!specification.getIncludeInactive()) {
                clauses.add("r.user."+User.ACTIVE_PROPERTY+"=true");
            }
        }

        if(null!=specification.getPkg()) {
            parameterAccumulator.add(specification.getPkg());
            clauses.add("r.pkg=?"+parameterAccumulator.size());

            if(!specification.getIncludeInactive()) {
                clauses.add("r.pkg."+Pkg.ACTIVE_PROPERTY+"=true");
            }
        }

        return String.join(" AND ", clauses);
    }

    public List<AuthorizationPkgRule> search(
            ObjectContext context,
            AuthorizationPkgRuleSearchSpecification specification) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(specification);
        Preconditions.checkState(specification.getOffset() >= 0);
        Preconditions.checkState(specification.getLimit() > 0);

        // special case.

        if(null!=specification.getPermissions() && specification.getPermissions().isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder queryBuilder = new StringBuilder();
        List<Object> parameterAccumulator = new ArrayList<>();
        String whereClause = prepareWhereClause(parameterAccumulator, context, specification);

        queryBuilder.append("SELECT r FROM ");
        queryBuilder.append(PermissionUserPkg.class.getSimpleName());
        queryBuilder.append(" AS r");

        if(whereClause.length() > 0) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(whereClause);
        }

        queryBuilder.append(" ORDER BY r.createTimestamp DESC");

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

        for(int i=0;i<parameterAccumulator.size();i++) {
            query.setParameter(i+1,parameterAccumulator.get(i));
        }

        query.setFetchLimit(specification.getLimit());
        query.setFetchOffset(specification.getOffset());

        //noinspection unchecked
        return (List<AuthorizationPkgRule>) context.performQuery(query);
    }

    public long total(
            ObjectContext context,
            AuthorizationPkgRuleSearchSpecification specification) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(specification);

        if(null!=specification.getPermissions() && specification.getPermissions().isEmpty()) {
            return 0;
        }

        StringBuilder queryBuilder = new StringBuilder();
        List<Object> parameterAccumulator = new ArrayList<>();
        String whereClause = prepareWhereClause(parameterAccumulator, context, specification);

        queryBuilder.append("SELECT COUNT(r) FROM ");
        queryBuilder.append(PermissionUserPkg.class.getSimpleName());
        queryBuilder.append(" AS r");

        if(whereClause.length() > 0) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(whereClause);
        }

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

        for(int i=0;i<parameterAccumulator.size();i++) {
            query.setParameter(i+1,parameterAccumulator.get(i));
        }

        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(query);

        switch(result.size()) {
            case 1:
                return result.get(0).longValue();

            default:
                throw new IllegalStateException("expected 1 row from count query, but got "+result.size());
        }
    }

    /**
     * <p>This method returns true if the proposed new rule would conflict with existing rules
     * that are already present in the system.</p>
     */

    public boolean wouldConflict(
            ObjectContext context,
            User user,
            Permission permission,
            Pkg pkg) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(permission);
        Preconditions.checkNotNull(user);

        Expression baseE = ExpressionFactory.matchExp(PermissionUserPkg.USER_PROPERTY, user)
                .andExp(ExpressionFactory.matchExp(PermissionUserPkg.PERMISSION_PROPERTY, permission));

        {
            SelectQuery query = new SelectQuery(
                    PermissionUserPkg.class,
                    baseE.andExp(ExpressionFactory.matchExp(PermissionUserPkg.PKG_PROPERTY, null)));

            if (!context.performQuery(query).isEmpty()) {
                return true;
            }
        }

        {
            SelectQuery query = new SelectQuery(
                    PermissionUserPkg.class,
                    baseE.andExp(ExpressionFactory.matchExp(PermissionUserPkg.PKG_PROPERTY, pkg)));

            if (!context.performQuery(query).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>This method will create a new rule object.  It will decide what sort of object to create
     * based on the inputs supplied.</p>
     */

    public AuthorizationPkgRule create(
            ObjectContext context,
            User user,
            Permission permission,
            Pkg pkg) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(permission);
        Preconditions.checkNotNull(user);

        if(user.getIsRoot()) {
            throw new IllegalStateException("when creating an authorization rule, the rule is not able to be applied to a root user");
        }

        PermissionUserPkg rule = context.newObject(PermissionUserPkg.class);
        rule.setPermission(permission);
        user.addToManyTarget(User.PERMISSION_USER_PKGS_PROPERTY, rule, true);
        rule.setPkg(pkg);
        LOGGER.info("did create permission user repository; {},{},{}", permission, user, pkg);

        return rule;
    }

    public void remove(
            ObjectContext context,
            User user,
            Permission permission,
            Pkg pkg) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(permission);
        Preconditions.checkNotNull(user);

        Optional<PermissionUserPkg> permissionUserPkgOptional = PermissionUserPkg.getByPermissionUserAndPkg(context, permission, user, pkg);

        if (permissionUserPkgOptional.isPresent()) {
            context.deleteObjects(permissionUserPkgOptional.get());
            LOGGER.info("did remove permission user package; {},{},{}", permission, user, pkg);
        } else {
            LOGGER.info("no permission user package already existed to remove; {},{},{}", permission, user, pkg);
        }

    }

    /**
     * <p>This method will generate a CSV dump of the rules.  This might be downloaded from a web-browser.</p>
     */

    public void generateCsv(
            ObjectContext context,
            OutputStream outputStream) throws IOException {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(outputStream);

        DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.basicDateTimeNoMillis();

        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
        CSVWriter csvWriter = new CSVWriter(outputStreamWriter);

        csvWriter.writeNext(new String[]{
                "create-timestamp",
                "user-nickname",
                "user-active",
                "permission-code",
                "permission-name",
                "pkg-name"
        });

        SelectQuery query = new SelectQuery(PermissionUserPkg.class);
        query.addOrdering(new Ordering(PermissionUserPkg.USER_PROPERTY + "." + User.NICKNAME_PROPERTY, SortOrder.ASCENDING));
        query.addOrdering(new Ordering(PermissionUserPkg.PERMISSION_PROPERTY+ "." + Permission.CODE_PROPERTY, SortOrder.ASCENDING));
        query.setFetchLimit(LIMIT_GENERATECSV);
        List<PermissionUserPkg> rules;

        do {
            rules = context.performQuery(query);

            for(PermissionUserPkg rule : rules) {
                csvWriter.writeNext(new String[] {
                        dateTimeFormatter.print(rule.getCreateTimestamp().getTime()),
                        rule.getUser().getNickname(),
                        Boolean.toString(rule.getUser().getActive()),
                        rule.getPermission().getCode(),
                        rule.getPermission().getName(),
                        null!=rule.getPkg() ? rule.getPkg().getName() : ""
                });
            }

            query.setFetchOffset(query.getFetchOffset() + LIMIT_GENERATECSV);
        }
        while(rules.size() >= LIMIT_GENERATECSV);

        csvWriter.flush();
        outputStreamWriter.flush();
    }


}
