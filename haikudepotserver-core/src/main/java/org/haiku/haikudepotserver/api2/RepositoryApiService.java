/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.PersistentObject;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.model.CreateRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceMirrorResult;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoriesRepository;
import org.haiku.haikudepotserver.api2.model.GetRepositoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoriesResult;
import org.haiku.haikudepotserver.api2.model.GetRepositoryRepositorySource;
import org.haiku.haikudepotserver.api2.model.GetRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoryResult;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceMirrorResult;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceRepositorySourceMirror;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceResult;
import org.haiku.haikudepotserver.api2.model.RemoveRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesRepository;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesResult;
import org.haiku.haikudepotserver.api2.model.TriggerImportRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositoryFilter;
import org.haiku.haikudepotserver.api2.model.UpdateRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceFilter;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceMirrorFilter;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Country;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceExtraIdentifier;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceMirror;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySourceMirror;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.support.exception.ValidationException;
import org.haiku.haikudepotserver.support.exception.ValidationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component("repositoryApiServiceV2")
public class RepositoryApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryApiService.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final RepositoryService repositoryService;
    private final JobService jobService;

    public RepositoryApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            RepositoryService repositoryService,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public void createRepository(CreateRepositoryRequestEnvelope request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.newContext();

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.REPOSITORY_ADD)) {
            throw new AccessDeniedException("unable to add a repository");
        }

        // the code must be supplied.

        if (Strings.isNullOrEmpty(request.getCode())) {
            throw new ValidationException(new ValidationFailure(Repository.CODE.getName(), "required"));
        }

        // check to see if there is an existing repository with the same code; non-unique.

        {
            Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, request.getCode());

            if (repositoryOptional.isPresent()) {
                throw new ValidationException(new ValidationFailure(Repository.CODE.getName(), "unique"));
            }
        }

        Repository repository = context.newObject(Repository.class);

        repository.setCode(request.getCode());
        repository.setName(request.getName());
        repository.setInformationUrl(request.getInformationUrl());

        context.commitChanges();
    }

    public void createRepositorySource(CreateRepositorySourceRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getCode()), "the code for the new repository source must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getRepositoryCode()), "the repository for the new repository source must be identified");

        final ObjectContext context = serverRuntime.newContext();
        Repository repository = getRepository(context, request.getRepositoryCode());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("unable to edit the repository [" + repository + "]");
        }

        Optional<RepositorySource> existingRepositorySourceOptional = RepositorySource.tryGetByCode(context, request.getCode());

        if(existingRepositorySourceOptional.isPresent()) {
            throw new ValidationException(new ValidationFailure(RepositorySource.CODE.getName(), "unique"));
        }

        RepositorySource repositorySource = context.newObject(RepositorySource.class);
        repositorySource.setRepository(repository);
        repositorySource.setCode(request.getCode());
        repository.setModifyTimestamp();
        context.commitChanges();

        LOGGER.info("did create a new repository source '{}' on the repository '{}'",
                repositorySource, repository);
    }


    public CreateRepositorySourceMirrorResult createRepositorySourceMirror(CreateRepositorySourceMirrorRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getRepositorySourceCode()), "the code for the new repository source mirror");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getCountryCode()), "the country code should be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getBaseUrl()), "the base url should be supplied");

        final ObjectContext context = serverRuntime.newContext();

        Country country = Country.tryGetByCode(context, request.getCountryCode())
                .orElseThrow(() -> new ObjectNotFoundException(
                        Country.class.getSimpleName(), request.getCountryCode()));
        RepositorySource repositorySource = getRepositorySource(context, request.getRepositorySourceCode());

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositorySource.getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("the repository [" + repositorySource.getRepository()
                    + "] is not able to be edited");
        }

        // check that a mirror with this base URL on this repository source
        // does not already exist.

        if (tryGetRepositorySourceMirrorObjectIdForBaseUrl(
                repositorySource.getCode(), request.getBaseUrl()).isPresent()) {
            LOGGER.info("attempt to add a repository source mirror for a url [{}] that is "
                    + " already in use", request.getBaseUrl());
            throw new ValidationException(new ValidationFailure(
                    RepositorySourceMirror.BASE_URL.getName(), "unique"));
        }

        // if there is no other mirror then this should be the primary.

        RepositorySourceMirror mirror = context.newObject(RepositorySourceMirror.class);
        mirror.setIsPrimary(repositorySource.tryGetPrimaryMirror().isEmpty());
        mirror.setBaseUrl(request.getBaseUrl());
        mirror.setRepositorySource(repositorySource);
        mirror.setCountry(country);
        mirror.setDescription(StringUtils.trimToNull(request.getDescription()));
        mirror.setCode(UUID.randomUUID().toString());

        repositorySource.getRepository().setModifyTimestamp();
        context.commitChanges();

        LOGGER.info("did add mirror [{}] to repository source [{}]",
                country.getCode(), repositorySource.getCode());

        return new CreateRepositorySourceMirrorResult()
                .code(mirror.getCode());
    }

    /**
     * <p>This will look for the {@link RepositorySourceMirror} that is related
     * to the nominated {@link RepositorySource} and has the supplied baseUrl.
     * This is so that a check can be made to ensure that there are not more
     * than one mirror with the same baseUrl on the one {@link RepositorySource}
     * .</p>
     */

    private Optional<ObjectId> tryGetRepositorySourceMirrorObjectIdForBaseUrl(
            String repositorySourceCode,
            String baseUrl) {
        ObjectContext context = serverRuntime.newContext();
        return RepositorySourceMirror.findByRepositorySource(
                        context,
                        RepositorySource.getByCode(context, repositorySourceCode),
                        true)
                .stream()
                .filter(rsm -> rsm.getBaseUrl().equals(baseUrl))
                .findFirst()
                .map(PersistentObject::getObjectId);
    }

    public GetRepositoriesResult getRepositories(GetRepositoriesRequestEnvelope request) {
        Preconditions.checkArgument(null != request);
        return new GetRepositoriesResult()
                .repositories(Repository.getAll(serverRuntime.newContext())
                        .stream()
                        .filter(r -> BooleanUtils.isTrue(request.getIncludeInactive()) || r.getActive())
                        .map(r -> new GetRepositoriesRepository()
                                .code(r.getCode())
                                .name(r.getName()))
                        .collect(Collectors.toList()));
    }

    public GetRepositoryResult getRepository(GetRepositoryRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getCode()));

        final ObjectContext context = serverRuntime.newContext();
        final Repository repository = getRepository(context, request.getCode());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_VIEW)) {
            throw new AccessDeniedException("unable to view repository [" + repository + "]");
        }

        return new GetRepositoryResult()
                .active(repository.getActive())
                .code(repository.getCode())
                .name(repository.getName())
                .createTimestamp(repository.getCreateTimestamp().getTime())
                .modifyTimestamp(repository.getModifyTimestamp().getTime())
                .informationUrl(repository.getInformationUrl())
                .hasPassword(StringUtils.isNotBlank(repository.getPasswordHash()))
                .repositorySources(repository.getRepositorySources()
                        .stream()
                        .filter(rs -> rs.getActive() || BooleanUtils.isTrue(request.getIncludeInactiveRepositorySources()))
                        .map(rs -> new GetRepositoryRepositorySource()
                                .active(rs.getActive())
                                .code(rs.getCode())
                                .url(rs.tryGetPrimaryMirror().map(_RepositorySourceMirror::getBaseUrl).orElse(null))
                                .identifier(rs.getIdentifier())
                                .architectureCode(Optional.ofNullable(rs.getArchitecture())
                                        .map(Architecture::getCode)
                                        .orElse(null))
                                .lastImportTimestamp(Optional.ofNullable(rs.getLastImportTimestamp()).map(Date::getTime).orElse(null))
                        )
                        .collect(Collectors.toUnmodifiableList())
                );
    }

    public GetRepositorySourceResult getRepositorySource(GetRepositorySourceRequestEnvelope request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getCode()));

        final ObjectContext context = serverRuntime.newContext();
        RepositorySource repositorySource = getRepositorySource(context, request.getCode());
        boolean canEdit = permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositorySource.getRepository(),
                Permission.REPOSITORY_EDIT);

        return new GetRepositorySourceResult()
                .active(repositorySource.getActive())
                .code(repositorySource.getCode())
                .repositoryCode(repositorySource.getRepository().getCode())
                .identifier(repositorySource.getIdentifier())
                .architectureCode(Optional.ofNullable(repositorySource.getArchitecture())
                        .map(Architecture::getCode)
                        .orElse(null))
                .lastImportTimestamp(Optional.ofNullable(repositorySource.getLastImportTimestamp()).map(Date::getTime).orElse(null))
                .extraIdentifiers(repositorySource.getExtraIdentifiers())
                .repositorySourceMirrors(repositorySource.getRepositorySourceMirrors()
                        .stream()
                        .filter(m -> m.getActive() || BooleanUtils.isTrue(request.getIncludeInactiveRepositorySourceMirrors()))
                        .sorted()
                        .map(rsm -> new GetRepositorySourceRepositorySourceMirror()
                                .active(rsm.getActive())
                                .baseUrl(rsm.getBaseUrl())
                                .countryCode(rsm.getCountry().getCode())
                                .isPrimary(rsm.getIsPrimary())
                                .code(rsm.getCode())
                        )
                        .collect(Collectors.toUnmodifiableList())
                )
                .forcedInternalBaseUrl(canEdit ? repositorySource.getForcedInternalBaseUrl() : null);
    }

    public GetRepositorySourceMirrorResult getRepositorySourceMirror(GetRepositorySourceMirrorRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be provided");
        Preconditions.checkArgument(StringUtils.isNotBlank(request.getCode()), "a mirror code must be provided");

        final ObjectContext context = serverRuntime.newContext();

        RepositorySourceMirror repositorySourceMirror = RepositorySourceMirror.tryGetByCode(context, request.getCode())
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), request.getCode()));

        return new GetRepositorySourceMirrorResult()
                .active(repositorySourceMirror.getActive())
                .baseUrl(repositorySourceMirror.getBaseUrl())
                .code(repositorySourceMirror.getCode())
                .countryCode(repositorySourceMirror.getCountry().getCode())
                .createTimestamp(repositorySourceMirror.getCreateTimestamp().getTime())
                .modifyTimestamp(repositorySourceMirror.getModifyTimestamp().getTime())
                .description(repositorySourceMirror.getDescription())
                .isPrimary(repositorySourceMirror.getIsPrimary())
                .repositorySourceCode(repositorySourceMirror.getRepositorySource().getCode());
    }

    public void removeRepositorySourceMirror(RemoveRepositorySourceMirrorRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request is required");
        Preconditions.checkArgument(StringUtils.isNotBlank(request.getCode()), "the code is required on the request");

        final ObjectContext context = serverRuntime.newContext();

        RepositorySourceMirror repositorySourceMirror = RepositorySourceMirror.tryGetByCode(context, request.getCode())
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), request.getCode()));

        if (repositorySourceMirror.getIsPrimary()) {
            throw new IllegalStateException("unable to remove the primary mirror");
        }

        repositorySourceMirror.getRepositorySource().getRepository().setModifyTimestamp();
        context.deleteObject(repositorySourceMirror);
        context.commitChanges();

        LOGGER.info("did remote the repository source mirror [{}]", request.getCode());
    }

    public SearchRepositoriesResult searchRepositories(SearchRepositoriesRequestEnvelope request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.newContext();

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.REPOSITORY_LIST)) {
            throw new AccessDeniedException("unable to view lists of repositories");
        }

        if (BooleanUtils.isTrue(request.getIncludeInactive())) {
            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    null,
                    Permission.REPOSITORY_LIST_INACTIVE)) {
                throw new AccessDeniedException("unable to view lists of inactive repositories");
            }
        }

        RepositorySearchSpecification specification = new RepositorySearchSpecification();
        specification.setExpression(
                Optional.ofNullable(request.getExpression())
                        .map(StringUtils::trimToNull)
                        .orElse(null));
        specification.setExpressionType(
                Optional.ofNullable(request.getExpressionType())
                        .map(et -> PkgSearchSpecification.ExpressionType.valueOf(request.getExpressionType().name()))
                        .orElse(null));
        specification.setLimit(request.getLimit());
        specification.setOffset(request.getOffset());
        specification.setIncludeInactive(BooleanUtils.isTrue(request.getIncludeInactive()));

        long total = repositoryService.total(context, specification);
        List<SearchRepositoriesRepository> items = Collections.emptyList();

        if (total > 0) {
            items = repositoryService.search(context, specification)
                    .stream()
                    .map(r -> new SearchRepositoriesRepository()
                            .active(r.getActive())
                            .name(r.getName())
                            .code(r.getCode()))
                    .collect(Collectors.toUnmodifiableList());
        }

        return new SearchRepositoriesResult()
                .total(total)
                .items(items);
    }

    public void triggerImportRepository(TriggerImportRepositoryRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getRepositoryCode()));
        Preconditions.checkArgument(null == request.getRepositorySourceCodes() || !request.getRepositorySourceCodes().isEmpty(), "bad repository sources");

        final ObjectContext context = serverRuntime.newContext();
        Repository repository = getRepository(context, request.getRepositoryCode());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_IMPORT)) {
            throw new AccessDeniedException("attempt to trigger repository import for [" + repository + "]");
        }

        RepositoryHpkrIngressJobSpecification specification = new RepositoryHpkrIngressJobSpecification();
        specification.setRepositoryCode(repository.getCode());

        if (null != request.getRepositorySourceCodes()) {
            specification.setRepositorySourceCodes(repository.getRepositorySources()
                    .stream()
                    .map(RepositorySource::getCode)
                    .filter(code -> request.getRepositorySourceCodes().contains(code))
                    .collect(Collectors.toUnmodifiableSet()));
        }

        jobService.submit(specification, JobSnapshot.COALESCE_STATUSES_QUEUED);
    }

    public void updateRepository(UpdateRepositoryRequestEnvelope request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.newContext();
        Repository repository = getRepository(context, request.getCode());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("unable to edit the repository [" + repository + "]");
        }

        for (UpdateRepositoryFilter filter : request.getFilter()) {
            switch (filter) {

                case ACTIVE:
                    if (null == request.getActive()) {
                        throw new IllegalStateException("the active flag must be supplied");
                    }

                    if (repository.getActive() != request.getActive()) {
                        repository.setActive(request.getActive());
                        LOGGER.info("did set the active flag on repository {} to {}", request.getCode(), request.getActive());
                    }

                    if (!request.getActive()) {
                        repository.getRepositorySources().forEach(rs -> {
                            rs.setActive(false);
                            LOGGER.info("did set the active flag on the repository source {} to false", rs.getCode());
                        });
                    }

                    break;

                case NAME:
                    if (null == request.getName()) {
                        throw new IllegalStateException("the name must be supplied to update the repository");
                    }

                    String name = request.getName().trim();

                    if (0 == name.length()) {
                        throw new ValidationException(new ValidationFailure(Repository.NAME.getName(), "invalid"));
                    }

                    repository.setName(name);
                    break;

                case INFORMATIONURL:
                    repository.setInformationUrl(request.getInformationUrl());
                    LOGGER.info("did set the information url on repository {} to {}", request.getCode(), request.getInformationUrl());
                    break;

                case PASSWORD:
                    if (StringUtils.isBlank(request.getPasswordClear())) {
                        repository.setPasswordSalt(null);
                        repository.setPasswordHash(null);
                        LOGGER.info("cleared the password for repository [{}]", repository);
                    } else {
                        repositoryService.setPassword(repository, request.getPasswordClear());
                        LOGGER.info("did update the repository [{}] password", repository);
                    }
                    break;

                default:
                    throw new IllegalStateException("unhandled filter for updating a repository");
            }
        }

        if (context.hasChanges()) {
            context.commitChanges();
        }
        else {
            LOGGER.info("update repository {} with no changes made", request.getCode());
        }
    }

    public void updateRepositorySource(UpdateRepositorySourceRequestEnvelope request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(StringUtils.isNotBlank(request.getCode()), "a code is required to identify the repository source to update");
        Preconditions.checkArgument(null != request.getFilter(), "filters must be provided to specify what aspects of the repository source should be updated");

        final ObjectContext context = serverRuntime.newContext();
        RepositorySource repositorySource = getRepositorySource(context, request.getCode());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositorySource.getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("cannot edit the repository [" + repositorySource.getRepository() + "]");
        }

        for (UpdateRepositorySourceFilter filter : request.getFilter()) {

            switch (filter) {

                case ACTIVE:
                    if (null == request.getActive()) {
                        throw new IllegalArgumentException("the active field must be provided if the request requires it to be updated");
                    }
                    repositorySource.setActive(request.getActive());
                    LOGGER.info("did set the repository source {} active to {}", repositorySource, request.getActive());
                    break;

                case FORCED_INTERNAL_BASE_URL:
                    repositorySource.setForcedInternalBaseUrl(
                            StringUtils.trimToNull(request.getForcedInternalBaseUrl()));
                    LOGGER.info("did set the repository source forced internal base url");
                    break;

                case EXTRA_IDENTIFIERS: {
                    Set<String> existing = Set.copyOf(repositorySource.getExtraIdentifiers());
                    Set<String> desired = Set.copyOf(CollectionUtils.emptyIfNull(request.getExtraIdentifiers()));
                    SetUtils.difference(existing, desired).stream()
                            .map(repositorySource::tryGetRepositorySourceExtraIdentifierForIdentifier)
                            .map(Optional::orElseThrow)
                            .forEach(rsei -> {
                                repositorySource.removeFromRepositorySourceExtraIdentifiers(rsei);
                                context.deleteObject(rsei);
                            });
                    SetUtils.difference(desired, existing).forEach(i -> {
                        RepositorySourceExtraIdentifier rsei = context.newObject(RepositorySourceExtraIdentifier.class);
                        rsei.setRepositorySource(repositorySource);
                        rsei.setIdentifier(i);
                        repositorySource.addToRepositorySourceExtraIdentifiers(rsei);
                    });
                    break;
                }

                default:
                    throw new IllegalStateException("unhandled filter; " + filter.name());

            }
        }

        if (context.hasChanges()) {
            repositorySource.getRepository().setModifyTimestamp();
            context.commitChanges();
        }
        else {
            LOGGER.info("update repository source {} with no changes made", repositorySource);
        }
    }

    public void updateRepositorySourceMirror(UpdateRepositorySourceMirrorRequestEnvelope request) {
        Preconditions.checkArgument(null!=request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getCode()), "the code for the mirror to update");

        final ObjectContext context = serverRuntime.newContext();

        RepositorySourceMirror repositorySourceMirror = RepositorySourceMirror.tryGetByCode(context, request.getCode())
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), request.getCode()));

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositorySourceMirror.getRepositorySource().getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("the repository [" + repositorySourceMirror.getRepositorySource().getRepository() + "] is unable to be edited");
        }

        for (UpdateRepositorySourceMirrorFilter filter : CollectionUtils.emptyIfNull(request.getFilter())) {
            switch (filter) {
                case ACTIVE:
                    if (repositorySourceMirror.getIsPrimary()) {
                        throw new ValidationException(new ValidationFailure(
                                RepositorySourceMirror.ACTIVE.getName(), "confict"));
                    }
                    repositorySourceMirror.setActive(BooleanUtils.isTrue(request.getActive()));
                    break;
                case BASE_URL:
                    if (StringUtils.isBlank(request.getBaseUrl())) {
                        throw new ValidationException(new ValidationFailure(
                                RepositorySourceMirror.BASE_URL.getName(), "required"));
                    }

                    if (!repositorySourceMirror.getBaseUrl().equals(request.getBaseUrl())) {
                        if (tryGetRepositorySourceMirrorObjectIdForBaseUrl(
                                repositorySourceMirror.getRepositorySource().getCode(),
                                request.getBaseUrl()).isPresent()) {
                            throw new ValidationException(new ValidationFailure(
                                    RepositorySourceMirror.BASE_URL.getName(), "unique"));
                        }

                        repositorySourceMirror.setBaseUrl(request.getBaseUrl());
                    }
                    break;
                case COUNTRY:
                    Country country = Country.tryGetByCode(context, request.getCountryCode())
                            .orElseThrow(() -> new ObjectNotFoundException(
                                    Country.class.getSimpleName(), request.getCountryCode()));
                    repositorySourceMirror.setCountry(country);
                    break;
                case IS_PRIMARY:
                    boolean isPrimary = BooleanUtils.isTrue(request.getIsPrimary());

                    if (isPrimary != repositorySourceMirror.getIsPrimary()) {
                        if (isPrimary) {
                            // in this case, the former primary should loose it's primary
                            // status so that it can be swapped to this one.
                            repositorySourceMirror.getRepositorySource().getPrimaryMirror().setIsPrimary(false);
                            repositorySourceMirror.setIsPrimary(true);
                        } else {
                            throw new ValidationException(new ValidationFailure(
                                    RepositorySourceMirror.IS_PRIMARY.getName(), "confict"));
                        }
                    }
                    break;
                case DESCRIPTION:
                    repositorySourceMirror.setDescription(StringUtils.trimToNull(request.getDescription()));
                    break;
                default:
                    throw new IllegalStateException("unknown change filter for mirror [" + filter + "]");
            }
        }

        if (context.hasChanges()) {
            repositorySourceMirror.getRepositorySource().getRepository().setModifyTimestamp();
        }

        context.commitChanges();
        LOGGER.info("did update mirror [{}]", repositorySourceMirror.getCode());
    }

}
