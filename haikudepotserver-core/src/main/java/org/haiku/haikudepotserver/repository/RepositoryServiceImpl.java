/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.repository.model.AlertRepositoryAbsentUpdateMail;
import org.haiku.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.LikeHelper;
import org.haiku.haikudepotserver.support.mail.model.MailSupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * <p>This service provides non-trivial operations and processes around repositories.</p>
 */

@Service
public class RepositoryServiceImpl implements RepositoryService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(RepositoryServiceImpl.class);

    private final PasswordEncoder passwordEncoder;

    private final MailSupportService mailSupportService;

    private final List<String> alertsRepositoryAbsentUpdatesTo;

    public RepositoryServiceImpl(
            PasswordEncoder passwordEncoder,
            MailSupportService mailSupportService,
            @Value("${hds.alerts.repository-absent-updates.to:}") List<String> alertsRepositoryAbsentUpdatesTo
    ) {
        this.passwordEncoder = Preconditions.checkNotNull(passwordEncoder);
        this.mailSupportService = Preconditions.checkNotNull(mailSupportService);
        this.alertsRepositoryAbsentUpdatesTo = alertsRepositoryAbsentUpdatesTo;
    }

    // ------------------------------
    // HELPERS

    @Override
    public Date getLastRepositoryModifyTimestampSecondAccuracy(ObjectContext context) {
        EJBQLQuery query = new EJBQLQuery(String.join(" ",
                "SELECT",
                "MAX(r." + Repository.MODIFY_TIMESTAMP.getName() + ")",
                "FROM",
                Repository.class.getSimpleName(),
                "r WHERE r.active = true"));

        query.setCacheGroup(HaikuDepot.CacheGroup.REPOSITORY.name());

        List<Object> result = context.performQuery(query);

        return switch (result.size()) {
            case 0 -> new Date(0);
            case 1 -> DateTimeHelper.secondAccuracyDate((Date) result.get(0));
            default -> throw new IllegalStateException("more than one row returned for a max aggregate.");
        };
    }

    @Override
    public List<Repository> getRepositoriesForPkg(
            ObjectContext context,
            Pkg pkg) {
        Preconditions.checkArgument(null!=context);
        Preconditions.checkArgument(null!=pkg);

        String ejbql = "SELECT DISTINCT r FROM\n" +
                Repository.class.getSimpleName() +
                " r WHERE r.active = true AND EXISTS (SELECT pv FROM \n" +
                PkgVersion.class.getSimpleName() +
                " pv WHERE pv.repositorySource.repository=r" +
                " AND pv.pkg=:pkg" +
                ") ORDER BY r.code";

        EJBQLQuery query = new EJBQLQuery(ejbql);
        query.setParameter("pkg", pkg);

        return (List<Repository>) context.performQuery(query);

    }

    // ------------------------------
    // SEARCH

    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            RepositorySearchSpecification search) {

        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<String> whereExpressions = new ArrayList<>();

        if (null != search.getExpression()) {
            switch (search.getExpressionType()) {

                case CONTAINS:
                    parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                    whereExpressions.add("LOWER(r." + Repository.CODE.getName() + ") LIKE ?" + parameterAccumulator.size() + " ESCAPE '|'");
                    break;

                default:
                    throw new IllegalStateException("unsupported expression type; "+search.getExpressionType());

            }
        }

        if (!search.getIncludeInactive()) {
            whereExpressions.add("r." + Repository.ACTIVE.getName() + " = true");
        }

        return String.join(" AND ", whereExpressions);
    }

    @Override
    public List<Repository> search(ObjectContext context, RepositorySearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        List<Object> parameterAccumulator = new ArrayList<>();

        StringBuilder ejbql = new StringBuilder();
        ejbql.append("SELECT r FROM ");
        ejbql.append(Repository.class.getSimpleName());
        ejbql.append(" r ");

        String whereClause = prepareWhereClause(parameterAccumulator, context, search);

        if (!Strings.isNullOrEmpty(whereClause)) {
            ejbql.append(" WHERE ");
            ejbql.append(whereClause);
        }

        ejbql.append(" ORDER BY r.");
        ejbql.append(Repository.CODE.getName());
        ejbql.append(" ASC");

        EJBQLQuery ejbqlQuery = new EJBQLQuery(ejbql.toString());

        for (int i = 0; i < parameterAccumulator.size(); i++) {
            ejbqlQuery.setParameter(i+1, parameterAccumulator.get(i));
        }

        ejbqlQuery.setFetchLimit(search.getLimit());
        ejbqlQuery.setFetchOffset(search.getOffset());

        //noinspection unchecked
        return (List<Repository>) context.performQuery(ejbqlQuery);
    }

    @Override
    public long total(ObjectContext context, RepositorySearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<Object> parameters = new ArrayList<>();

        StringBuilder ejbql = new StringBuilder();
        ejbql.append("SELECT COUNT(r) FROM Repository AS r");

        String whereClause = prepareWhereClause(parameters, context, search);

        if (!Strings.isNullOrEmpty(whereClause)) {
            ejbql.append(" WHERE ");
            ejbql.append(whereClause);
        }

        EJBQLQuery ejbQuery = new EJBQLQuery(ejbql.toString());

        for (int i = 0; i < parameters.size(); i++) {
            ejbQuery.setParameter(i + 1, parameters.get(i));
        }

        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(ejbQuery);

        if (result.size() == 1) {
            return result.get(0).longValue();
        }
        throw new IllegalStateException("expected 1 row from count query, but got " + result.size());
    }

    public void setPassword(Repository repository, String passwordClear) {
        Preconditions.checkArgument(null != repository, "the repository is required");
        if (StringUtils.isBlank(passwordClear)) {
            repository.setPasswordSalt(null);
            repository.setPasswordHash(null);
        }
        else {
            List<String> parts = Splitter.on(".").splitToList(passwordEncoder.encode(passwordClear));
            if (2 != parts.size()) {
                throw new IllegalStateException("expecting a salt and hash separated by a period symbol");
            }
            repository.setPasswordSalt(parts.get(0));
            repository.setPasswordHash(parts.get(1));
        }
    }

    public boolean matchPassword(Repository repository, String passwordClear) {
        return StringUtils.isNotBlank(repository.getPasswordSalt())
                && StringUtils.isNotBlank(repository.getPasswordHash())
                && passwordEncoder.matches(
                passwordClear,
                repository.getPasswordSalt() + "." + repository.getPasswordHash());
    }

    @Override
    public void alertForRepositoriesAbsentUpdates(ObjectContext context) {
        java.util.Date now = new java.sql.Timestamp(System.currentTimeMillis());

        List<RepositorySource> repositorySources = ObjectSelect
                .query(RepositorySource.class)
                .where(RepositorySource.ACTIVE.isTrue())
                .and(RepositorySource.EXPECTED_UPDATE_FREQUENCY_HOURS.isNotNull())
                .orderBy(RepositorySource.CODE.asc())
                .select(context);

        AlertRepositoryAbsentUpdateMail mailModel = new AlertRepositoryAbsentUpdateMail(
                repositorySources.stream()
                        .map(rs -> maybeMapToRepositorySourceAbsentUpdate(now, context, rs))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList()
        );

        if (mailModel.getRepositorySourceAbsentUpdates().isEmpty()) {
            LOGGER.info("did not find any repository sources requiring alerts for absent updates");
        } else {
            mailSupportService.sendMail(
                    alertsRepositoryAbsentUpdatesTo,
                    mailModel,
                    "alertrepositoryabsentupdate",
                    NaturalLanguageCoordinates.english());
        }
    }

    /**
     * <p>If the supplied {@link RepositorySource} has no update for longer than the expected number of hours
     * then return a record with the details to be sent out as an email; otherwise empty Optional.</p>
     */

    private static Optional<AlertRepositoryAbsentUpdateMail.RepositorySourceAbsentUpdate> maybeMapToRepositorySourceAbsentUpdate(
            java.util.Date now,
            ObjectContext context,
            RepositorySource repositorySource
    ) {
        Preconditions.checkArgument(null != repositorySource.getExpectedUpdateFrequencyHours());

        PkgVersion pkgVersion = ObjectSelect
                .query(PkgVersion.class)
                .orderBy(PkgVersion.MODIFY_TIMESTAMP.desc())
                .selectFirst(context);
        Long hoursAgo = Optional.ofNullable(pkgVersion)
                .map(PkgVersion::getModifyTimestamp)
                .map(mt -> TimeUnit.MILLISECONDS.toHours(now.getTime() - mt.getTime()))
                .orElse(null);

        if (null == hoursAgo || hoursAgo > repositorySource.getExpectedUpdateFrequencyHours()) {
            return Optional.of(new AlertRepositoryAbsentUpdateMail.RepositorySourceAbsentUpdate(
                    repositorySource.getCode(),
                    repositorySource.getExpectedUpdateFrequencyHours(),
                    null == hoursAgo ? null : hoursAgo.intValue()
            ));
        }

        return Optional.empty();
    }

}
