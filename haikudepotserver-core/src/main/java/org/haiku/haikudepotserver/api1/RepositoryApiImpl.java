/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.PersistentObject;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api1.model.repository.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.api1.support.ValidationException;
import org.haiku.haikudepotserver.api1.support.ValidationFailure;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySourceMirror;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component("repositoryApiImplV1")
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/repository") // TODO; legacy path - remove
public class RepositoryApiImpl extends AbstractApiImpl implements RepositoryApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryApiImpl.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final UserAuthenticationService userAuthenticationService;
    private final RepositoryService repositoryService;
    private final JobService jobService;

    public RepositoryApiImpl(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            UserAuthenticationService userAuthenticationService,
            RepositoryService repositoryService,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.userAuthenticationService = Preconditions.checkNotNull(userAuthenticationService);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    @Override
    public GetRepositoriesResult getRepositories(final GetRepositoriesRequest getRepositoriesRequest) {
        Preconditions.checkArgument(null!=getRepositoriesRequest);
        GetRepositoriesResult result = new GetRepositoriesResult();
        boolean includeInactive = null!=getRepositoriesRequest.includeInactive && getRepositoriesRequest.includeInactive;
        result.repositories = Repository.getAll(serverRuntime.newContext())
                .stream()
                .filter(r -> includeInactive || r.getActive())
                .map(r -> {
                    GetRepositoriesResult.Repository resultRepository = new GetRepositoriesResult.Repository();
                    resultRepository.code = r.getCode();
                    resultRepository.name = r.getName();
                    return resultRepository;
                })
                .collect(Collectors.toList());
        return result;
    }

    // note; no integration test for this one.
    @Override
    public TriggerImportRepositoryResult triggerImportRepository(
            TriggerImportRepositoryRequest triggerImportRepositoryRequest) {

        Preconditions.checkNotNull(triggerImportRepositoryRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(triggerImportRepositoryRequest.repositoryCode));
        Preconditions.checkArgument(null==triggerImportRepositoryRequest.repositorySourceCodes || !triggerImportRepositoryRequest.repositorySourceCodes.isEmpty(), "bad repository sources");

        final ObjectContext context = serverRuntime.newContext();

        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, triggerImportRepositoryRequest.repositoryCode);

        if (repositoryOptional.isEmpty()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), triggerImportRepositoryRequest.repositoryCode);
        }

        Repository repository = repositoryOptional.get();

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_IMPORT)) {
            throw new AccessDeniedException("attempt to trigger repository import for [" + repository + "]");
        }

        Set<RepositorySource> repositorySources = null;

        if (null != triggerImportRepositoryRequest.repositorySourceCodes) {

            repositorySources = new HashSet<>();

            for (String repositorySourceCode : triggerImportRepositoryRequest.repositorySourceCodes) {
                repositorySources.add(
                        repository
                                .getRepositorySources()
                                .stream()
                                .filter(rs -> rs.getCode().equals(repositorySourceCode))
                                .collect(SingleCollector.optional())
                                .orElseThrow(() -> new ObjectNotFoundException(RepositorySource.class.getSimpleName(), repositorySourceCode))
                );
            }
        }

        jobService.submit(
                new RepositoryHpkrIngressJobSpecification(
                        repository.getCode(),
                        null==repositorySources ? null : repositorySources
                                .stream()
                                .map(RepositorySource::getCode)
                                .collect(Collectors.toSet())
                ),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        return new TriggerImportRepositoryResult();
    }

    @Override
    public SearchRepositoriesResult searchRepositories(SearchRepositoriesRequest request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.newContext();

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.REPOSITORY_LIST)) {
            throw new AccessDeniedException("unable to view lists of repositories");
        }

        if (null != request.includeInactive && request.includeInactive) {
            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    null,
                    Permission.REPOSITORY_LIST_INACTIVE)) {
                throw new AccessDeniedException("unable to view lists of inactive repositories");
            }
        }

        RepositorySearchSpecification specification = new RepositorySearchSpecification();
        String exp = request.expression;

        if (null != exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if (null != request.expressionType) {
            specification.setExpressionType(
                    PkgSearchSpecification.ExpressionType.valueOf(request.expressionType.name()));
        }

        specification.setLimit(request.limit);
        specification.setOffset(request.offset);
        specification.setIncludeInactive(null!=request.includeInactive && request.includeInactive);

        SearchRepositoriesResult result = new SearchRepositoriesResult();

        result.total = repositoryService.total(context, specification);
        result.items = Collections.emptyList();

        if (result.total > 0) {
            List<Repository> searchedRepositories = repositoryService.search(context, specification);

            result.items = searchedRepositories.stream().map(sr -> {
                SearchRepositoriesResult.Repository resultRepository = new SearchRepositoriesResult.Repository();
                resultRepository.active = sr.getActive();
                resultRepository.name = sr.getName();
                resultRepository.code = sr.getCode();
                return resultRepository;
            }).collect(Collectors.toList());
        }

        return result;
    }

    @Override
    public GetRepositoryResult getRepository(GetRepositoryRequest getRepositoryRequest) {
        Preconditions.checkNotNull(getRepositoryRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getRepositoryRequest.code));

        final ObjectContext context = serverRuntime.newContext();
        final Repository repository = getRepositoryOrThrow(context, getRepositoryRequest.code);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_VIEW)) {
            throw new AccessDeniedException("unable to view repository [" + repository + "]");
        }

        GetRepositoryResult result = new GetRepositoryResult();
        result.active = repository.getActive();
        result.code = repository.getCode();
        result.name = repository.getName();
        result.createTimestamp = repository.getCreateTimestamp().getTime();
        result.modifyTimestamp = repository.getModifyTimestamp().getTime();
        result.informationUrl = repository.getInformationUrl();
        result.hasPassword = StringUtils.isNotBlank(repository.getPasswordHash());
        result.repositorySources = repository.getRepositorySources()
                .stream()
                .filter(
                        rs -> rs.getActive() ||
                                (null != getRepositoryRequest.includeInactiveRepositorySources &&
                                        getRepositoryRequest.includeInactiveRepositorySources)
                )
                .map(rs -> {
                    GetRepositoryResult.RepositorySource resultRs = new GetRepositoryResult.RepositorySource();
                    resultRs.active = rs.getActive();
                    resultRs.code = rs.getCode();
                    resultRs.url = rs.tryGetPrimaryMirror().map(_RepositorySourceMirror::getBaseUrl).orElse(null);
                    resultRs.identifier = rs.getIdentifier();

                    if (null != rs.getLastImportTimestamp()) {
                        resultRs.lastImportTimestamp = rs.getLastImportTimestamp().getTime();
                    }

                    return resultRs;
                })
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public UpdateRepositoryResult updateRepository(UpdateRepositoryRequest updateRepositoryRequest) {
        Preconditions.checkNotNull(updateRepositoryRequest);

        final ObjectContext context = serverRuntime.newContext();
        Repository repository = getRepositoryOrThrow(context, updateRepositoryRequest.code);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("unable to edit the repository [" + repository + "]");
        }

        for (UpdateRepositoryRequest.Filter filter : updateRepositoryRequest.filter) {
            switch (filter) {

                case ACTIVE:
                    if (null==updateRepositoryRequest.active) {
                        throw new IllegalStateException("the active flag must be supplied");
                    }

                    if (repository.getActive() != updateRepositoryRequest.active) {
                        repository.setActive(updateRepositoryRequest.active);
                        LOGGER.info("did set the active flag on repository {} to {}", updateRepositoryRequest.code, updateRepositoryRequest.active);
                    }

                    if (!updateRepositoryRequest.active) {
                        for (RepositorySource repositorySource : repository.getRepositorySources()) {
                            if (repositorySource.getActive()) {
                                repositorySource.setActive(false);
                                LOGGER.info("did set the active flag on the repository source {} to false", repositorySource.getCode());
                            }
                        }
                    }

                    break;

                case NAME:
                    if (null == updateRepositoryRequest.name) {
                        throw new IllegalStateException("the name must be supplied to update the repository");
                    }

                    String name = updateRepositoryRequest.name.trim();

                    if (0 == name.length()) {
                        throw new ValidationException(new ValidationFailure(Repository.NAME.getName(), "invalid"));
                    }

                    repository.setName(name);
                    break;

                case INFORMATIONURL:
                    repository.setInformationUrl(updateRepositoryRequest.informationUrl);
                    LOGGER.info("did set the information url on repository {} to {}", updateRepositoryRequest.code, updateRepositoryRequest.informationUrl);
                    break;

                case PASSWORD:
                    if (StringUtils.isBlank(updateRepositoryRequest.passwordClear)) {
                        repository.setPasswordSalt(null);
                        repository.setPasswordHash(null);
                        LOGGER.info("cleared the password for repository [{}]", repository);
                    } else {
                        repositoryService.setPassword(repository, updateRepositoryRequest.passwordClear);
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
            LOGGER.info("update repository {} with no changes made", updateRepositoryRequest.code);
        }

        return new UpdateRepositoryResult();
    }

    @Override
    public CreateRepositoryResult createRepository(
            CreateRepositoryRequest createRepositoryRequest) {

        Preconditions.checkNotNull(createRepositoryRequest);

        final ObjectContext context = serverRuntime.newContext();

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.REPOSITORY_ADD)) {
            throw new AccessDeniedException("unable to add a repository");
        }

        // the code must be supplied.

        if (Strings.isNullOrEmpty(createRepositoryRequest.code)) {
            throw new ValidationException(new ValidationFailure(Repository.CODE.getName(), "required"));
        }

        // check to see if there is an existing repository with the same code; non-unique.

        {
            Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, createRepositoryRequest.code);

            if (repositoryOptional.isPresent()) {
                throw new ValidationException(new ValidationFailure(Repository.CODE.getName(), "unique"));
            }
        }

        Repository repository = context.newObject(Repository.class);

        repository.setCode(createRepositoryRequest.code);
        repository.setName(createRepositoryRequest.name);
        repository.setInformationUrl(createRepositoryRequest.informationUrl);

        context.commitChanges();

        return new CreateRepositoryResult();
    }

    @Override
    public GetRepositorySourceResult getRepositorySource(
            GetRepositorySourceRequest request) {
        Preconditions.checkArgument(null!=request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.code));

        final ObjectContext context = serverRuntime.newContext();
        Optional<RepositorySource> repositorySourceOptional = RepositorySource.tryGetByCode(context, request.code);

        if (repositorySourceOptional.isEmpty()) {
            throw new ObjectNotFoundException(RepositorySource.class.getSimpleName(), request.code);
        }

        RepositorySource repositorySource = repositorySourceOptional.get();

        GetRepositorySourceResult result = new GetRepositorySourceResult();
        result.active = repositorySource.getActive();
        result.code = repositorySource.getCode();
        result.repositoryCode = repositorySource.getRepository().getCode();
        result.identifier = repositorySource.getIdentifier();

        if (null != repositorySource.getLastImportTimestamp()) {
            result.lastImportTimestamp = repositorySource.getLastImportTimestamp().getTime();
        }

        result.extraIdentifiers = repositorySource.getExtraIdentifiers();

        result.repositorySourceMirrors = repositorySource.getRepositorySourceMirrors()
                .stream()
                .filter(m -> m.getActive() || BooleanUtils.isTrue(request.includeInactiveRepositorySourceMirrors))
                .sorted()
                .map(rsm -> {
                    GetRepositorySourceResult.RepositorySourceMirror mirror =
                            new GetRepositorySourceResult.RepositorySourceMirror();
                    mirror.active = rsm.getActive();
                    mirror.baseUrl = rsm.getBaseUrl();
                    mirror.countryCode = rsm.getCountry().getCode();
                    mirror.isPrimary = rsm.getIsPrimary();
                    mirror.code = rsm.getCode();
                    return mirror;
                })
                .collect(Collectors.toList());

        if (permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositorySource.getRepository(),
                Permission.REPOSITORY_EDIT)) {
            result.forcedInternalBaseUrl = repositorySourceOptional.get().getForcedInternalBaseUrl();
        }

        return result;
    }

    @Override
    public UpdateRepositorySourceResult updateRepositorySource(UpdateRepositorySourceRequest request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(StringUtils.isNotBlank(request.code), "a code is required to identify the repository source to update");
        Preconditions.checkArgument(null != request.filter, "filters must be provided to specify what aspects of the repository source should be updated");

        final ObjectContext context = serverRuntime.newContext();
        RepositorySource repositorySource = getRepositorySourceOrThrow(context, request.code);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositorySource.getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("cannot edit the repository [" + repositorySource.getRepository() + "]");
        }

        for (UpdateRepositorySourceRequest.Filter filter : request.filter) {

            switch (filter) {

                case ACTIVE:
                    if (null == request.active) {
                        throw new IllegalArgumentException("the active field must be provided if the request requires it to be updated");
                    }
                    repositorySource.setActive(request.active);
                    LOGGER.info("did set the repository source {} active to {}", repositorySource, request.active);
                    break;

                case FORCED_INTERNAL_BASE_URL:
                    repositorySource.setForcedInternalBaseUrl(
                            StringUtils.trimToNull(request.forcedInternalBaseUrl));
                    LOGGER.info("did set the repository source forced internal base url");
                    break;

                case EXTRA_IDENTIFIERS: {
                    Set<String> existing = Set.copyOf(repositorySource.getExtraIdentifiers());
                    Set<String> desired = Set.copyOf(CollectionUtils.emptyIfNull(request.extraIdentifiers));
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

        return new UpdateRepositorySourceResult();

    }

    @Override
    public CreateRepositorySourceResult createRepositorySource(CreateRepositorySourceRequest request) {

        Preconditions.checkArgument(null!=request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.code), "the code for the new repository source must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository for the new repository source must be identified");

        final ObjectContext context = serverRuntime.newContext();
        Repository repository = getRepositoryOrThrow(context, request.repositoryCode);

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repository,
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("unable to edit the repository [" + repository + "]");
        }

        Optional<RepositorySource> existingRepositorySourceOptional = RepositorySource.tryGetByCode(context, request.code);

        if(existingRepositorySourceOptional.isPresent()) {
            throw new ValidationException(new ValidationFailure(RepositorySource.CODE.getName(), "unique"));
        }

        RepositorySource repositorySource = context.newObject(RepositorySource.class);
        repositorySource.setRepository(repository);
        repositorySource.setCode(request.code);
        repository.setModifyTimestamp();
        context.commitChanges();

        LOGGER.info("did create a new repository source '{}' on the repository '{}'",
                repositorySource, repository);

        return new CreateRepositorySourceResult();
    }

    @Override
    public CreateRepositorySourceMirrorResult createRepositorySourceMirror(
            CreateRepositorySourceMirrorRequest request) {
        Preconditions.checkArgument(null!=request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositorySourceCode), "the code for the new repository source mirror");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.countryCode), "the country code should be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.baseUrl), "the base url should be supplied");

        final ObjectContext context = serverRuntime.newContext();

        Country country = Country.tryGetByCode(context, request.countryCode)
                .orElseThrow(() -> new ObjectNotFoundException(
                        Country.class.getSimpleName(), request.countryCode));
        RepositorySource repositorySource = getRepositorySourceOrThrow(context, request.repositorySourceCode);

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
                repositorySource.getCode(), request.baseUrl).isPresent()) {
            LOGGER.info("attempt to add a repository source mirror for a url [{}] that is "
                    + " already in use", request.baseUrl);
            throw new ValidationException(new ValidationFailure(
                    RepositorySourceMirror.BASE_URL.getName(), "unique"));
        }

        // if there is no other mirror then this should be the primary.

        RepositorySourceMirror mirror = context.newObject(RepositorySourceMirror.class);
        mirror.setIsPrimary(repositorySource.tryGetPrimaryMirror().isEmpty());
        mirror.setBaseUrl(request.baseUrl);
        mirror.setRepositorySource(repositorySource);
        mirror.setCountry(country);
        mirror.setDescription(StringUtils.trimToNull(request.description));
        mirror.setCode(UUID.randomUUID().toString());

        repositorySource.getRepository().setModifyTimestamp();
        context.commitChanges();

        LOGGER.info("did add mirror [{}] to repository source [{}]",
                country.getCode(), repositorySource.getCode());

        CreateRepositorySourceMirrorResult result = new CreateRepositorySourceMirrorResult();
        result.code = mirror.getCode();
        return result;
    }

    @Override
    public UpdateRepositorySourceMirrorResult updateRepositorySourceMirror(UpdateRepositorySourceMirrorRequest request) {
        Preconditions.checkArgument(null!=request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.code), "the code for the mirror to update");

        final ObjectContext context = serverRuntime.newContext();

        RepositorySourceMirror repositorySourceMirror = RepositorySourceMirror.tryGetByCode(context, request.code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), request.code));

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositorySourceMirror.getRepositorySource().getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AccessDeniedException("the repository [" + repositorySourceMirror.getRepositorySource().getRepository() + "] is unable to be edited");
        }

        for (UpdateRepositorySourceMirrorRequest.Filter filter : CollectionUtils.emptyIfNull(request.filter)) {
            switch (filter) {
                case ACTIVE:
                    if (repositorySourceMirror.getIsPrimary()) {
                        throw new ValidationException(new ValidationFailure(
                                RepositorySourceMirror.ACTIVE.getName(), "confict"));
                    }
                    repositorySourceMirror.setActive(null != request.active && request.active);
                    break;
                case BASE_URL:
                    if (StringUtils.isBlank(request.baseUrl)) {
                        throw new ValidationException(new ValidationFailure(
                                RepositorySourceMirror.BASE_URL.getName(), "required"));
                    }

                    if (!repositorySourceMirror.getBaseUrl().equals(request.baseUrl)) {
                        if (tryGetRepositorySourceMirrorObjectIdForBaseUrl(
                                repositorySourceMirror.getRepositorySource().getCode(),
                                request.baseUrl).isPresent()) {
                            throw new ValidationException(new ValidationFailure(
                                    RepositorySourceMirror.BASE_URL.getName(), "unique"));
                        }

                        repositorySourceMirror.setBaseUrl(request.baseUrl);
                    }
                    break;
                case COUNTRY:
                    Country country = Country.tryGetByCode(context, request.countryCode)
                            .orElseThrow(() -> new ObjectNotFoundException(
                                    Country.class.getSimpleName(), request.countryCode));
                    repositorySourceMirror.setCountry(country);
                    break;
                case IS_PRIMARY:
                    boolean isPrimary = null != request.isPrimary && request.isPrimary;

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
                    repositorySourceMirror.setDescription(StringUtils.trimToNull(request.description));
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
        return new UpdateRepositorySourceMirrorResult();
    }

    @Override
    public GetRepositorySourceMirrorResult getRepositorySourceMirror(
            GetRepositorySourceMirrorRequest request) {

        Preconditions.checkArgument(null != request, "the request must be provided");
        Preconditions.checkArgument(StringUtils.isNotBlank(request.code), "a mirror code must be provided");

        final ObjectContext context = serverRuntime.newContext();

        RepositorySourceMirror repositorySourceMirror = RepositorySourceMirror.tryGetByCode(context, request.code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), request.code));

        GetRepositorySourceMirrorResult result = new GetRepositorySourceMirrorResult();
        result.active = repositorySourceMirror.getActive();
        result.baseUrl = repositorySourceMirror.getBaseUrl();
        result.code = repositorySourceMirror.getCode();
        result.countryCode = repositorySourceMirror.getCountry().getCode();
        result.createTimestamp = repositorySourceMirror.getCreateTimestamp().getTime();
        result.modifyTimestamp = repositorySourceMirror.getModifyTimestamp().getTime();
        result.description = repositorySourceMirror.getDescription();
        result.isPrimary = repositorySourceMirror.getIsPrimary();
        result.repositorySourceCode = repositorySourceMirror.getRepositorySource().getCode();
        return result;
    }

    @Override
    public RemoveRepositorySourceMirrorResult removeRepositorySourceMirror(
            RemoveRepositorySourceMirrorRequest request) {
        Preconditions.checkArgument(null != request, "the request is required");
        Preconditions.checkArgument(StringUtils.isNotBlank(request.code), "the code is required on the request");

        final ObjectContext context = serverRuntime.newContext();

        RepositorySourceMirror repositorySourceMirror = RepositorySourceMirror.tryGetByCode(context, request.code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), request.code));

        if (repositorySourceMirror.getIsPrimary()) {
            throw new IllegalStateException("unable to remove the primary mirror");
        }

        repositorySourceMirror.getRepositorySource().getRepository().setModifyTimestamp();
        context.deleteObject(repositorySourceMirror);
        context.commitChanges();

        LOGGER.info("did remote the repository source mirror [{}]", request.code);

        return new RemoveRepositorySourceMirrorResult();
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

    private Repository getRepositoryOrThrow(
            ObjectContext context, String code) {
        return Repository.tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        Repository.class.getSimpleName(), code));
    }

    private RepositorySource getRepositorySourceOrThrow(
            ObjectContext context, String code) {
        return RepositorySource.tryGetByCode(context, code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySource.class.getSimpleName(), code));
    }

}
