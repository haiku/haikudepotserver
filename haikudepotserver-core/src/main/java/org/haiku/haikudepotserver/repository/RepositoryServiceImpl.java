/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
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
        java.sql.Timestamp maxModifyTimestamp = ObjectSelect.query(Repository.class)
                .where(Repository.ACTIVE.isTrue())
                .column(Repository.MODIFY_TIMESTAMP.max())
                .selectOne(context);
        return DateTimeHelper.secondAccuracyDate(maxModifyTimestamp);
    }

    @Override
    public List<Repository> getRepositoriesForPkg(
            ObjectContext context,
            Pkg pkg) {
        Preconditions.checkArgument(null!=context);
        Preconditions.checkArgument(null!=pkg);

        List<String> repositoryCodes = ObjectSelect.query(PkgVersion.class)
                .where(PkgVersion.PKG.eq(pkg))
                .and(
                        PkgVersion.REPOSITORY_SOURCE
                                .dot(RepositorySource.REPOSITORY)
                                .dot(Repository.ACTIVE)
                                .isTrue()
                )
                .orderBy(PkgVersion.REPOSITORY_SOURCE
                        .dot(RepositorySource.REPOSITORY)
                        .dot(Repository.ACTIVE)
                        .asc()
                )
                .column(PkgVersion.REPOSITORY_SOURCE
                        .dot(RepositorySource.REPOSITORY)
                        .dot(Repository.CODE)
                )
                .distinct()
                .select(context);

        return ObjectSelect.query(Repository.class).where(
                        ExpressionFactory.and(
                                repositoryCodes.stream()
                                        .map(Repository.CODE::eq)
                                        .toList()
                        )
                )
                .orderBy(Repository.CODE.asc())
                .select(context);
    }

    // ------------------------------
    // SEARCH

    private ObjectSelect<Repository> prepareWhereClause(
            ObjectSelect<Repository> objectSelect,
            RepositorySearchSpecification search) {

        Preconditions.checkNotNull(objectSelect);
        Preconditions.checkNotNull(search);

        if (null != search.getExpression()) {
            switch (search.getExpressionType()) {

                case CONTAINS:
                    String likeExpression = "%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%";
                    objectSelect = objectSelect.and(Repository.CODE.lower().like(likeExpression, '|'));
                    break;

                default:
                    throw new IllegalStateException("unsupported expression type; "+search.getExpressionType());

            }
        }

        if (!search.getIncludeInactive()) {
            objectSelect = objectSelect.and(Repository.ACTIVE.isTrue());
        }

        return objectSelect;
    }

    @Override
    public List<Repository> search(ObjectContext context, RepositorySearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);
        return prepareWhereClause(ObjectSelect.query(Repository.class), search)
                .orderBy(Repository.CODE.asc())
                .limit(search.getLimit())
                .offset(search.getOffset())
                .select(context);
    }

    @Override
    public long total(ObjectContext context, RepositorySearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        return prepareWhereClause(ObjectSelect.query(Repository.class), search).selectCount(context);
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
                .where(PkgVersion.IMPORT_TIMESTAMP.isNotNull())
                .orderBy(PkgVersion.IMPORT_TIMESTAMP.desc())
                .selectFirst(context);

        if (null == pkgVersion) {
            LOGGER.warn("for the repository source [{}] no package versions were found with import timestamps", repositorySource);
        } else {
            Long hoursAgo = Optional.of(pkgVersion)
                    .map(PkgVersion::getImportTimestamp)
                    .map(mt -> TimeUnit.MILLISECONDS.toHours(now.getTime() - mt.getTime()))
                    .orElse(null);

            if (null == hoursAgo || hoursAgo > repositorySource.getExpectedUpdateFrequencyHours()) {
                return Optional.of(new AlertRepositoryAbsentUpdateMail.RepositorySourceAbsentUpdate(
                        repositorySource.getCode(),
                        repositorySource.getExpectedUpdateFrequencyHours(),
                        null == hoursAgo ? null : hoursAgo.intValue()
                ));
            }
        }

        return Optional.empty();
    }

}
