/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.access.OptimisticLockException;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._HaikuDepot;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.pkg.model.PkgSupplementModificationAgent;
import org.haiku.haikudepotserver.pkg.model.PkgSupplementModificationService;
import org.haiku.haikudepotserver.support.*;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>This service undertakes non-trivial operations on packages.</p>
 */

@Service
public class PkgServiceImpl implements PkgService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgServiceImpl.class);

    private final static int BATCH_SIZE = 200;

    private final static List<String> SUFFIXES_SUBORDINATE_PKG_NAMES = ImmutableList.of(
            SUFFIX_PKG_DEVELOPMENT,
            SUFFIX_PKG_X86,
            SUFFIX_PKG_X86 + SUFFIX_PKG_DEVELOPMENT);

    // TODO; should be injected as a pattern because this should not know about paths for the controller.
    public final static String URL_SEGMENT_PKGDOWNLOAD = "__pkgdownload";

    private final String defaultArchitectureCode;

    private final PkgSupplementModificationService pkgSupplementModificationService;

    public PkgServiceImpl(
            @Value("${hds.architecture.default.code}") String defaultArchitectureCode,
            PkgSupplementModificationService pkgSupplementModificationService) {
        this.defaultArchitectureCode = defaultArchitectureCode;
        this.pkgSupplementModificationService = Preconditions.checkNotNull(pkgSupplementModificationService);
    }

    // ------------------------------
    // QUERY

    /**
     * <p>This method will return the latest version for a package in any architecture.</p>
     */

    @Override
    public Optional<PkgVersion> getLatestPkgVersionForPkg(
            ObjectContext context,
            Pkg pkg,
            RepositorySource repositorySource) {

        Preconditions.checkArgument(null != context, "a context must be provided");
        Preconditions.checkArgument(null != pkg, "a package must be provided");

        Optional<PkgVersion> pkgVersionOptional = getLatestPkgVersionForPkg(
                context,
                pkg,
                repositorySource,
                Collections.singletonList(Architecture.getByCode(context, defaultArchitectureCode)));

        if(pkgVersionOptional.isEmpty()) {
            List<Architecture> architectures = Architecture.getAllExceptByCode(
                    context,
                    ImmutableList.of(Architecture.CODE_SOURCE, defaultArchitectureCode));

            for (int i = 0; i < architectures.size() && pkgVersionOptional.isEmpty(); i++) {
                pkgVersionOptional = getLatestPkgVersionForPkg(
                        context,
                        pkg,
                        repositorySource,
                        Collections.singletonList(architectures.get(i)));
            }
        }

        return pkgVersionOptional;
    }

    /**
     * <p>This method will return the latest PkgVersion for the supplied package.</p>
     */

    @Override
    public Optional<PkgVersion> getLatestPkgVersionForPkg(
            ObjectContext context,
            Pkg pkg,
            RepositorySource repositorySource,
            final List<Architecture> architectures) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must must be provided");
        Preconditions.checkArgument(null != architectures && !architectures.isEmpty(), "the architectures must be provided and must not be empty");
        Preconditions.checkArgument(null != repositorySource, "the repository source must be provided");

        return Optional.ofNullable(ObjectSelect.query(PkgVersion.class)
                .where(PkgVersion.PKG.eq(pkg))
                .and(PkgVersion.ACTIVE.isTrue())
                .and(PkgVersion.IS_LATEST.isTrue())
                .and(PkgVersion.ARCHITECTURE.in(architectures))
                .and(PkgVersion.REPOSITORY_SOURCE.dot(RepositorySource.ACTIVE).isTrue())
                .and(PkgVersion.REPOSITORY_SOURCE.eq(repositorySource))
                .selectOne(context));
    }

    /**
     * <p>For the given architecture and package, re-establish what is the latest package and correct it.
     * This may be necessary after, for example, adjusting the active flag on a pkg version.</p>
     * @return the updated latest package version or an empty option if there is none.
     */

    @Override
    public Optional<PkgVersion> adjustLatest(
            ObjectContext context,
            Pkg pkg,
            Architecture architecture) {

        Preconditions.checkArgument(null != context, "a context is required");
        Preconditions.checkArgument(null != pkg, "the package must be supplied");
        Preconditions.checkArgument(null != architecture, "the architecture must be supplied");

        List<PkgVersion> pkgVersions = ObjectSelect.query(PkgVersion.class)
                .where(PkgVersion.PKG.eq(pkg))
                .and(PkgVersion.ARCHITECTURE.eq(architecture))
                .select(context);

        if(!pkgVersions.isEmpty()) {

            final VersionCoordinatesComparator comparator = new VersionCoordinatesComparator();

            Optional<PkgVersion> pkgVersionOptional = pkgVersions
                    .stream()
                    .filter(PkgVersion::getActive)
                    .sorted((pv1, pv2) -> comparator.compare(pv2.toVersionCoordinates(), pv1.toVersionCoordinates()))
                    .findFirst();

            pkgVersionOptional.ifPresent(pv -> pv.setIsLatest(true));

            for (PkgVersion pkgVersion : pkgVersions) {
                if (pkgVersion.getIsLatest() &&
                        (pkgVersionOptional.isEmpty() ||
                                !pkgVersion.equals(pkgVersionOptional.get())
                        )
                        ) {
                    pkgVersion.setIsLatest(false);
                }
            }

            return pkgVersionOptional;
        }

        return Optional.empty();
    }

    /**
     * <p>Given a {@link PkgVersion}, see if there is a corresponding source package.</p>
     */

    @Override
    public Optional<PkgVersion> getCorrespondingSourcePkgVersion(
            ObjectContext context,
            PkgVersion pkgVersion) {

        Preconditions.checkArgument(null != context, "a context is required");
        Preconditions.checkArgument(null != pkgVersion, "a pkg version is required");

        Optional<Pkg> pkgSourceOptional = Pkg.tryGetByName(context, pkgVersion.getPkg().getName() + SUFFIX_PKG_SOURCE);

        return pkgSourceOptional.map(pkg -> ObjectSelect.query(PkgVersion.class)
                .where(PkgVersion.PKG.eq(pkg))
                .and(PkgVersion.REPOSITORY_SOURCE.eq(pkgVersion.getRepositorySource()))
                .and(PkgVersion.ACTIVE.isTrue())
                .and(PkgVersion.ARCHITECTURE.eq(Architecture.getByCode(context, Architecture.CODE_SOURCE)))
                .and(ExpressionHelper.toExpression(pkgVersion.toVersionCoordinates(), null))
                .selectOne(context));

    }

    // ------------------------------
    // SEARCH

    @Override
    public List<PkgVersion> search(
            ObjectContext context,
            PkgSearchSpecification search) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(search.getNaturalLanguage());
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        SQLTemplate sqlTemplate = (SQLTemplate) context.getEntityResolver()
                .getQueryDescriptor(_HaikuDepot.SEARCH_PKG_VERSIONS_QUERYNAME).buildQuery();
        Query query = sqlTemplate.createQuery(ImmutableMap.of(
                "search", search,
                "isTotal", false,
                "englishNaturalLanguage", NaturalLanguage.getEnglish(context)
        ));

        return (List<PkgVersion>) context.performQuery(query);
    }

    /**
     * <p>This method will provide a total of the package versions.</p>
     */

    @Override
    public long total(
            ObjectContext context,
            PkgSearchSpecification search) {

        SQLTemplate sqlTemplate = (SQLTemplate) context.getEntityResolver()
                .getQueryDescriptor(_HaikuDepot.SEARCH_PKG_VERSIONS_QUERYNAME).buildQuery();

        SQLTemplate query = (SQLTemplate) sqlTemplate.createQuery(ImmutableMap.of(
                "search", search,
                "isTotal", true,
                "englishNaturalLanguage", NaturalLanguage.getEnglish(context)
        ));
        query.setFetchingDataRows(true);

        DataRow dataRow = (DataRow) (context.performQuery(query)).getFirst();
        Number newTotal = (Number) dataRow.get("total");

        return newTotal.longValue();
    }

    // ------------------------------
    // EACH PACKAGE

    /**
     * <p>This method will provide a total of the packages.</p>
     */

    @Override
    public long totalPkg(
            ObjectContext context,
            boolean includeDevelopment) {
        Preconditions.checkArgument(null!=context, "the object context must be provided");
        List<DataRow> dataRows = MappedSelect.query(_HaikuDepot.ALL_ACTIVE_PKG_NAMES_QUERYNAME, DataRow.class)
                .param("isTotal", true)
                .param("includeDevelopment", includeDevelopment)
                .select(context);
        return dataRows.stream()
                .map(dr -> (Long) dr.get("total"))
                .collect(SingleCollector.single());
    }

    /**
     * <p>This will be called for each package in the system.</p>
     * @param c is the callback to invoke.
     * @param includeDevelopment when true implies that a package can be processed which only has versions that are for
     *                        the source architecture.
     * @return the quantity of packages processed.
     */

    @Override
    public long eachPkg(
            ObjectContext context,
            boolean includeDevelopment,
            StoppableConsumer<Pkg> c) {
        Preconditions.checkArgument(null!=c, "the callback should be provided to run for each package");
        Preconditions.checkArgument(null!=context, "the object context must be provided");

        int offset = 0;

        Preconditions.checkArgument(null!=context, "the object context must be provided");
        MappedSelect<DataRow> query = MappedSelect.query(_HaikuDepot.ALL_ACTIVE_PKG_NAMES_QUERYNAME, DataRow.class)
                .param("isTotal", false)
                .param("includeDevelopment", includeDevelopment)
                .limit(BATCH_SIZE);

        while (true) {

            var constrainedQuery = query.offset(offset);
            List<String> pkgNames = constrainedQuery.select(context)
                    .stream()
                    .map(dr -> (String) dr.get("name"))
                    .toList();

            if (pkgNames.isEmpty()) {
                return offset;
            }

            List<Pkg> pkgs = ObjectSelect.query(Pkg.class)
                    .where(Pkg.NAME.in(pkgNames))
                    .orderBy(Pkg.NAME.asc())
                    .select(context);

            for (Pkg pkg : pkgs) {
                if (!c.accept(pkg)) {
                    return offset;
                }
                offset++;
            }

            offset += pkgs.size();
        }
    }

    // ------------------------------
    // CHANGE LOG

    /**
     * <p>Performs necessary modifications to the package so that the changelog is updated
     * with the new content supplied.</p>
     */

    @Override
    public void updatePkgChangelog(
            ObjectContext context,
            PkgSupplementModificationAgent agent,
            PkgSupplement pkgSupplement,
            String newContent) {

        Preconditions.checkArgument(null != context, "the context is not supplied");
        Preconditions.checkArgument(null != pkgSupplement, "the pkg supplement is not supplied");
        Preconditions.checkArgument(null != agent, "the agent is not supplied");

        Optional<PkgChangelog> pkgChangelogOptional = pkgSupplement.getPkgChangelog();
        newContent = StringUtils.trimToNull(newContent);

        if (null == newContent) {
            pkgChangelogOptional.ifPresent(pcl -> {
                context.deleteObject(pkgChangelogOptional.get());

                pkgSupplementModificationService.appendModification(
                        context,
                        pkgSupplement,
                        agent,
                        String.format("removed the changelog for [%s]", pkgSupplement.getBasePkgName()));

                LOGGER.info("did remove the changelog for; {}", pkgSupplement.getBasePkgName());
            });
        } else {
            String oldContent = pkgChangelogOptional
                    .map(PkgChangelog::getContent)
                    .map(StringUtils::trimToNull)
                    .orElse(null);
            newContent = newContent.replace("\r\n", "\n"); // windows to unix newline.

            PkgChangelog pkgChangelog = pkgChangelogOptional.orElseGet(() -> {
                PkgChangelog created = context.newObject(PkgChangelog.class);
                created.setPkgSupplement(pkgSupplement);
                return created;
            });

            pkgChangelog.setContent(newContent);

            // write a pkg supplement modification entry for the change.

            pkgSupplementModificationService.appendModification(
                    context,
                    pkgSupplement,
                    agent,
                    String.format("updated the changelog for [%s];\n%s",
                            pkgSupplement.getBasePkgName(),
                            StringUtils.abbreviateMiddle(newContent, "...", 80)));

            LOGGER.info("did update the changelog for; {}", pkgSupplement.getBasePkgName());
        }

        pkgSupplement.setModifyTimestamp();
    }

    /**
     * <p>This method will deactivate package versions for a package where the package version is related to the
     * supplied repository.  This is used in the situation where a package was once part of a repository, but has
     * been removed.</p>
     * @return the quantity of package versions that were deactivated.
     */

    @Override
    public int deactivatePkgVersionsForPkgAssociatedWithRepositorySource(
            ObjectContext context,
            Pkg pkg,
            final RepositorySource repositorySource) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");
        Preconditions.checkArgument(null != repositorySource, "the repository source must be provided");

        int count = 0;

        for (PkgVersion pkgVersion : PkgVersion.findForPkg(context, pkg, repositorySource, false)) { // active only
            if (pkgVersion.getRepositorySource().equals(repositorySource)) {
                if (pkgVersion.getActive()) {
                    pkgVersion.setActive(false);
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * <p>This method will return all of the package names that have package versions that are related to a
     * repository.</p>
     */

    @Override
    public Set<String> fetchPkgNamesWithAnyPkgVersionAssociatedWithRepositorySource(
            ObjectContext context,
            RepositorySource repositorySource) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != repositorySource, "the repository soures must be provided");

        String queryBuilder = "SELECT p.name FROM " +
                Pkg.class.getSimpleName() +
                " p WHERE EXISTS(SELECT pv FROM " +
                PkgVersion.class.getSimpleName() +
                " pv WHERE pv." +
                PkgVersion.PKG.getName() +
                "=p AND pv." +
                PkgVersion.REPOSITORY_SOURCE.getName() +
                "=:repositorySource)";

        EJBQLQuery query = new EJBQLQuery(queryBuilder);
        query.setParameter("repositorySource", repositorySource);

        return ImmutableSet.copyOf(context.performQuery(query));
    }

    /**
     * <p>This method will either find the existing pkg prominence with respect to the
     * repository or will create one and return it.</p>
     */

    @Override
    public PkgProminence ensurePkgProminence(
            ObjectContext objectContext,
            Pkg pkg,
            Repository repository) {
        return ensurePkgProminence(objectContext, pkg, repository, Prominence.ORDERING_LAST);
    }

    @Override
    public PkgProminence ensurePkgProminence(
            ObjectContext objectContext,
            Pkg pkg,
            Repository repository,
            Integer ordering) {
        Preconditions.checkArgument(null!=ordering && ordering > 0, "an ordering must be suppied");
        return ensurePkgProminence(
                objectContext, pkg, repository,
                Prominence.getByOrdering(objectContext, ordering));
    }

    @Override
    public PkgProminence ensurePkgProminence(
            ObjectContext objectContext,
            Pkg pkg,
            Repository repository,
            Prominence prominence) {
        Preconditions.checkArgument(null != prominence, "the prominence must be provided");
        Preconditions.checkArgument(null != repository, "the repository must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");
        Optional<PkgProminence> pkgProminenceOptional = pkg.tryGetPkgProminence(repository);

        if (pkgProminenceOptional.isEmpty()) {
            PkgProminence pkgProminence = objectContext.newObject(PkgProminence.class);
            pkg.addToManyTarget(Pkg.PKG_PROMINENCES.getName(), pkgProminence, true);
            pkgProminence.setRepository(repository);
            pkgProminence.setProminence(prominence);
            return pkgProminence;
        }

        return pkgProminenceOptional.get();
    }

    @Override
    public Optional<String> tryGetMainPkgNameForSubordinatePkg(
            final String subordinatePkgName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(subordinatePkgName), "the pkg must be provided");

        return SUFFIXES_SUBORDINATE_PKG_NAMES
                .stream()
                .filter(subordinatePkgName::endsWith)
                .map(suffix -> StringUtils.removeEnd(subordinatePkgName, suffix))
                .findFirst();
    }

    @Override
    public String createHpkgDownloadUrl(PkgVersion pkgVersion) {
        return pkgVersion.tryGetHpkgURL(ExposureType.EXTERNAL_FACING)
                .filter(u -> ImmutableSet.of("http", "https").contains(u.getProtocol()))
                .map(URL::toString)
                .orElseGet(() -> {
                    UriComponentsBuilder builder = UriComponentsBuilder.fromPath(URL_SEGMENT_PKGDOWNLOAD);
                    pkgVersion.appendPathSegments(builder);
                    builder.path("package.hpkg");
                    return builder.build().toUriString();
                });
    }

    /**
     * <p>This method will update the {@link PkgCategory} set in the
     * nominated {@link Pkg} such that the supplied set are the
     * categories for the package.  It will do this by adding and removing relationships between the package
     * and the categories.</p>
     * @return true if a change was made.
     */

    @Override
    public boolean updatePkgCategories(
            ObjectContext context,
            PkgSupplementModificationAgent agent,
            Pkg pkg,
            List<PkgCategory> pkgCategories) {

        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != pkg, "the pkg must be provided");
        Preconditions.checkArgument(null != pkgCategories, "the pkg categories must be provided");
        Preconditions.checkArgument(null != agent, "the agent must be provided");

        PkgSupplement pkgSupplement = pkg.getPkgSupplement();
        pkgCategories = new ArrayList<>(pkgCategories);
        Set<String> removedCodes = new HashSet<>();
        Set<String> addedCodes = new HashSet<>();

        // now go through and delete any of those pkg relationships to packages that are already present
        // and which are no longer required.  Also remove those that we already have from the list.

        for (PkgPkgCategory pkgPkgCategory : ImmutableList.copyOf(pkgSupplement.getPkgPkgCategories())) {
            if (!pkgCategories.contains(pkgPkgCategory.getPkgCategory())) {
                pkgSupplement.removeToManyTarget(PkgSupplement.PKG_PKG_CATEGORIES.getName(), pkgPkgCategory, true);
                removedCodes.add(pkgPkgCategory.getPkgCategory().getCode());
                context.deleteObjects(pkgPkgCategory);
            }
            else {
                pkgCategories.remove(pkgPkgCategory.getPkgCategory());
            }
        }

        // now any remaining in the pkgCategories will need to be added to the pkg.

        for (PkgCategory pkgCategory : pkgCategories) {
            PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
            pkgPkgCategory.setPkgCategory(pkgCategory);
            pkgSupplement.addToManyTarget(PkgSupplement.PKG_PKG_CATEGORIES.getName(), pkgPkgCategory, true);
            addedCodes.add(pkgCategory.getCode());
        }

        // record the change

        if (!removedCodes.isEmpty() || !addedCodes.isEmpty()) {

            pkgSupplement.setModifyTimestamp();

            String content = String.format("categories changed for pkg [%s]\nremoved: %s\nadded: %s",
                    pkgSupplement.getBasePkgName(),
                    removedCodes.stream().sorted().collect(Collectors.joining(",")),
                    addedCodes.stream().sorted().collect(Collectors.joining(",")));

            pkgSupplementModificationService.appendModification(
                    context,
                    pkgSupplement,
                    agent,
                    content);

            return true;
        }

        return false;
    }

    /**
     * <p>This method will increment the view counter on a package version.  If it encounters an optimistic
     * locking problem then it will pause and it will try again in a moment.  It will attempt this a few
     * times and then fail with a runtime exception.</p>
     */

    @Override
    public void incrementViewCounter(ServerRuntime serverRuntime, ObjectId pkgVersionOid) {

        Preconditions.checkArgument(null != serverRuntime, "the server runtime must be provided");
        Preconditions.checkArgument(null != pkgVersionOid, "the pkg version oid must be provided");
        Preconditions.checkArgument(pkgVersionOid.getEntityName().equals(PkgVersion.class.getSimpleName()), "the oid must reference PkgVersion");

        int attempts = 3;

        while (true) {
            ObjectContext contextEdit = serverRuntime.newContext();
            PkgVersion pkgVersionEdit = ((List<PkgVersion>) contextEdit.performQuery(new ObjectIdQuery(pkgVersionOid)))
                    .stream()
                    .collect(SingleCollector.single());
            pkgVersionEdit.incrementViewCounter();

            try {
                contextEdit.commitChanges();
                LOGGER.info("did increment the view counter for '{}'", pkgVersionEdit.getPkg().toString());
                return;
            } catch (OptimisticLockException ole) {
                contextEdit.invalidateObjects(pkgVersionEdit);

                attempts--;

                if (0 == attempts) {
                    throw new RuntimeException("unable to increment the view counter for '"+pkgVersionEdit.getPkg().toString()+"' because of an optimistic locking failure; have exhausted attempts", ole);
                } else {
                    LOGGER.error("unable to increment the view counter for '{}' because of an optimistic locking failure; will try again...", pkgVersionEdit.getPkg().toString());
                    Uninterruptibles.sleepUninterruptibly(250 + (System.currentTimeMillis() % 250), TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    @Override
    public Date getLastModifyTimestampSecondAccuracy(ObjectContext context, RepositorySource repositorySource) {
        Preconditions.checkNotNull(context);

        Date pkgVersionMax = ObjectUtils.firstNonNull(
                ObjectSelect
                        .query(PkgVersion.class)
                        .where(PkgVersion.ACTIVE.isTrue())
                        .and(PkgVersion.REPOSITORY_SOURCE.eq(repositorySource))
                        .and(PkgVersion.PKG.dot(Pkg.ACTIVE).isTrue())
                        .max(PkgVersion.MODIFY_TIMESTAMP)
                        .sharedCache()
                        .cacheGroup(HaikuDepot.CacheGroup.PKG.name())
                        .selectFirst(context),
                new Date(0L));

        Date pkgMax = ObjectUtils.firstNonNull(
                ObjectSelect
                        .query(PkgVersion.class)
                        .where(PkgVersion.ACTIVE.isTrue())
                        .and(PkgVersion.REPOSITORY_SOURCE.eq(repositorySource))
                        .and(PkgVersion.PKG.dot(Pkg.ACTIVE).isTrue())
                        .max(PkgVersion.PKG.dot(Pkg.MODIFY_TIMESTAMP))
                        .sharedCache()
                        .cacheGroup(HaikuDepot.CacheGroup.PKG.name())
                        .selectFirst(context),
                new Date(0L));

        Date pkgSupplementMax = ObjectUtils.firstNonNull(
                ObjectSelect
                        .query(PkgVersion.class)
                        .where(PkgVersion.ACTIVE.isTrue())
                        .and(PkgVersion.REPOSITORY_SOURCE.eq(repositorySource))
                        .and(PkgVersion.PKG.dot(Pkg.ACTIVE).isTrue())
                        .max(PkgVersion.PKG.dot(Pkg.PKG_SUPPLEMENT).dot(PkgSupplement.MODIFY_TIMESTAMP))
                        .sharedCache()
                        .cacheGroup(HaikuDepot.CacheGroup.PKG.name())
                        .selectFirst(context),
                new Date(0L));

        return DateTimeHelper.secondAccuracyDate(new Date(
                Math.max(
                        Math.max(pkgVersionMax.getTime(), pkgMax.getTime()),
                        pkgSupplementMax.getTime())));
    }

    @Override
    public String createVanityLinkUrl(Pkg pkg) {
        return "/" + pkg.getName();
    }


}
