/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api1.model.repository.*;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.api1.support.ValidationException;
import org.haiku.haikudepotserver.api1.support.ValidationFailure;
import org.haiku.haikudepotserver.dataobjects.Country;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceMirror;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySourceMirror;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.security.model.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/repository") // TODO; legacy path - remove
public class RepositoryApiImpl extends AbstractApiImpl implements RepositoryApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryApiImpl.class);

    private final ServerRuntime serverRuntime;
    private final AuthorizationService authorizationService;
    private final RepositoryService repositoryService;
    private final JobService jobService;

    public RepositoryApiImpl(
            ServerRuntime serverRuntime,
            AuthorizationService authorizationService,
            RepositoryService repositoryService,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.authorizationService = Preconditions.checkNotNull(authorizationService);
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
            TriggerImportRepositoryRequest triggerImportRepositoryRequest)
            throws ObjectNotFoundException {

        Preconditions.checkNotNull(triggerImportRepositoryRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(triggerImportRepositoryRequest.repositoryCode));
        Preconditions.checkArgument(null==triggerImportRepositoryRequest.repositorySourceCodes || !triggerImportRepositoryRequest.repositorySourceCodes.isEmpty(), "bad repository sources");

        final ObjectContext context = serverRuntime.newContext();

        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, triggerImportRepositoryRequest.repositoryCode);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), triggerImportRepositoryRequest.repositoryCode);
        }

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                repositoryOptional.get(),
                Permission.REPOSITORY_IMPORT)) {
            throw new AuthorizationFailureException();
        }

        Set<RepositorySource> repositorySources = null;

        if(null!=triggerImportRepositoryRequest.repositorySourceCodes) {

            repositorySources = new HashSet<>();

             for(String repositorySourceCode : triggerImportRepositoryRequest.repositorySourceCodes) {
                 repositorySources.add(
                         repositoryOptional.get()
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
                        repositoryOptional.get().getCode(),
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

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                null,
                Permission.REPOSITORY_LIST)) {
            throw new AuthorizationFailureException();
        }

        if(null!=request.includeInactive && request.includeInactive) {
            if(!authorizationService.check(
                    context,
                    tryObtainAuthenticatedUser(context).orElse(null),
                    null,
                    Permission.REPOSITORY_LIST_INACTIVE)) {
                throw new AuthorizationFailureException();
            }
        }

        RepositorySearchSpecification specification = new RepositorySearchSpecification();
        String exp = request.expression;

        if(null!=exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if(null!=request.expressionType) {
            specification.setExpressionType(
                    PkgSearchSpecification.ExpressionType.valueOf(request.expressionType.name()));
        }

        specification.setLimit(request.limit);
        specification.setOffset(request.offset);
        specification.setIncludeInactive(null!=request.includeInactive && request.includeInactive);
        specification.setRepositorySourceSearchUrls(request.repositorySourceSearchUrls);

        SearchRepositoriesResult result = new SearchRepositoriesResult();

        result.total = repositoryService.total(context,specification);
        result.items = Collections.emptyList();

        if(result.total > 0) {
            List<Repository> searchedRepositories = repositoryService.search(context,specification);

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
    public GetRepositoryResult getRepository(GetRepositoryRequest getRepositoryRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getRepositoryRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getRepositoryRequest.code));

        final ObjectContext context = serverRuntime.newContext();

        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, getRepositoryRequest.code);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), getRepositoryRequest.code);
        }

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                repositoryOptional.get(),
                Permission.REPOSITORY_VIEW)) {
            throw new AuthorizationFailureException();
        }

        GetRepositoryResult result = new GetRepositoryResult();
        result.active = repositoryOptional.get().getActive();
        result.code = repositoryOptional.get().getCode();
        result.name = repositoryOptional.get().getName();
        result.createTimestamp = repositoryOptional.get().getCreateTimestamp().getTime();
        result.modifyTimestamp = repositoryOptional.get().getModifyTimestamp().getTime();
        result.informationUrl = repositoryOptional.get().getInformationUrl();
        result.repositorySources = repositoryOptional.get().getRepositorySources()
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
                    resultRs.repoInfoUrl = rs.getUrl();
                    return resultRs;
                })
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public UpdateRepositoryResult updateRepository(UpdateRepositoryRequest updateRepositoryRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(updateRepositoryRequest);

        final ObjectContext context = serverRuntime.newContext();

        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, updateRepositoryRequest.code);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), updateRepositoryRequest.code);
        }

        authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                repositoryOptional.get(),
                Permission.REPOSITORY_EDIT);

        for(UpdateRepositoryRequest.Filter filter : updateRepositoryRequest.filter) {
            switch(filter) {

                case ACTIVE:
                    if(null==updateRepositoryRequest.active) {
                        throw new IllegalStateException("the active flag must be supplied");
                    }

                    if(repositoryOptional.get().getActive() != updateRepositoryRequest.active) {
                        repositoryOptional.get().setActive(updateRepositoryRequest.active);
                        LOGGER.info("did set the active flag on repository {} to {}", updateRepositoryRequest.code, updateRepositoryRequest.active);
                    }

                    if(!updateRepositoryRequest.active) {
                        for(RepositorySource repositorySource : repositoryOptional.get().getRepositorySources()) {
                            if(repositorySource.getActive()) {
                                repositorySource.setActive(false);
                                LOGGER.info("did set the active flag on the repository source {} to false", repositorySource.getCode());
                            }
                        }
                    }

                    break;

                case NAME:
                    if(null==updateRepositoryRequest.name) {
                        throw new IllegalStateException("the name must be supplied to update the repository");
                    }

                    String name = updateRepositoryRequest.name.trim();

                    if(0==name.length()) {
                        throw new ValidationException(new ValidationFailure(Repository.NAME.getName(), "invalid"));
                    }

                    repositoryOptional.get().setName(name);
                    break;

                case INFORMATIONURL:
                    repositoryOptional.get().setInformationUrl(updateRepositoryRequest.informationUrl);
                    LOGGER.info("did set the information url on repository {} to {}", updateRepositoryRequest.code, updateRepositoryRequest.informationUrl);
                    break;

                default:
                    throw new IllegalStateException("unhandled filter for updating a repository");
            }
        }

        if(context.hasChanges()) {
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

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                null,
                Permission.REPOSITORY_ADD)) {
            throw new AuthorizationFailureException();
        }

        // the code must be supplied.

        if(Strings.isNullOrEmpty(createRepositoryRequest.code)) {
            throw new ValidationException(new ValidationFailure(Repository.CODE.getName(), "required"));
        }

        // check to see if there is an existing repository with the same code; non-unique.

        {
            Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, createRepositoryRequest.code);

            if(repositoryOptional.isPresent()) {
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
    public GetRepositorySourceResult getRepositorySource(GetRepositorySourceRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.code));

        final ObjectContext context = serverRuntime.newContext();

        Optional<RepositorySource> repositorySourceOptional = RepositorySource.tryGetByCode(context, request.code);

        if(!repositorySourceOptional.isPresent()) {
            throw new ObjectNotFoundException(RepositorySource.class.getSimpleName(), request.code);
        }

        GetRepositorySourceResult result = new GetRepositorySourceResult();
        result.active = repositorySourceOptional.get().getActive();
        result.code = repositorySourceOptional.get().getCode();
        result.repositoryCode = repositorySourceOptional.get().getRepository().getCode();
        result.url = repositorySourceOptional.get().getUrl();
        result.repositorySourceMirrors = repositorySourceOptional.get().getRepositorySourceMirrors()
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
        return result;
    }

    @Override
    public UpdateRepositorySourceResult updateRepositorySource(UpdateRepositorySourceRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.code), "a code is required to identify the repository source to update");
        Preconditions.checkArgument(null!=request.filter, "filters must be provided to specify what aspects of the repository source should be updated");

        final ObjectContext context = serverRuntime.newContext();

        Optional<RepositorySource> repositorySourceOptional = RepositorySource.tryGetByCode(context, request.code);

        if(!repositorySourceOptional.isPresent()) {
            throw new ObjectNotFoundException(RepositorySource.class.getSimpleName(), request.code);
        }

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                repositorySourceOptional.get().getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AuthorizationFailureException();
        }

        for(UpdateRepositorySourceRequest.Filter filter : request.filter) {

            switch(filter) {

                case ACTIVE:
                    if(null==request.active) {
                        throw new IllegalArgumentException("the active field must be provided if the request requires it to be updated");
                    }
                    repositorySourceOptional.get().setActive(request.active);
                    LOGGER.info("did set the repository source {} active to {}", repositorySourceOptional.get(), request.active);
                    break;

                default:
                    throw new IllegalStateException("unhandled filter; " + filter.name());

            }

        }

        if(context.hasChanges()) {
            repositorySourceOptional.get().getRepository().setModifyTimestamp();
            context.commitChanges();
        }
        else {
            LOGGER.info("update repository source {} with no changes made", repositorySourceOptional.get());
        }

        return new UpdateRepositorySourceResult();

    }

    @Override
    public CreateRepositorySourceResult createRepositorySource(CreateRepositorySourceRequest request) throws ObjectNotFoundException {

        Preconditions.checkArgument(null!=request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.code), "the code for the new repository source must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository for the new repository source must be identified");

        final ObjectContext context = serverRuntime.newContext();

        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, request.repositoryCode);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), request.repositoryCode);
        }

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                repositoryOptional.get(),
                Permission.REPOSITORY_EDIT)) {
            throw new AuthorizationFailureException();
        }

        Optional<RepositorySource> existingRepositorySourceOptional = RepositorySource.tryGetByCode(context, request.code);

        if(existingRepositorySourceOptional.isPresent()) {
            throw new ValidationException(new ValidationFailure(RepositorySource.CODE.getName(), "unique"));
        }

        RepositorySource repositorySource = context.newObject(RepositorySource.class);
        repositorySource.setRepository(repositoryOptional.get());
        repositorySource.setCode(request.code);
        repositoryOptional.get().setModifyTimestamp();
        context.commitChanges();

        LOGGER.info("did create a new repository source '{}' on the repository '{}'", repositorySource, repositoryOptional.get());

        return new CreateRepositorySourceResult();
    }

    @Override
    public CreateRepositorySourceMirrorResult createRepositorySourceMirror(CreateRepositorySourceMirrorRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositorySourceCode), "the code for the new repository source mirror");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.countryCode), "the country code should be supplied");

        final ObjectContext context = serverRuntime.newContext();

        Country country = Country.tryGetByCode(context, request.countryCode)
                .orElseThrow(() -> new ObjectNotFoundException(
                        Country.class.getSimpleName(), request.countryCode));
        RepositorySource repositorySource = RepositorySource.tryGetByCode(context, request.repositorySourceCode)
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySource.class.getSimpleName(), request.repositorySourceCode));

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                repositorySource.getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AuthorizationFailureException();
        }

        // if there is no other mirror then this should be the primary.

        RepositorySourceMirror mirror = context.newObject(RepositorySourceMirror.class);
        mirror.setIsPrimary(!repositorySource.tryGetPrimaryMirror().isPresent());
        mirror.setBaseUrl(request.baseUrl);
        mirror.setRepositorySource(repositorySource);
        mirror.setCountry(country);
        mirror.setDescription(StringUtils.trimToNull(request.description));
        mirror.setCode(UUID.randomUUID().toString());
        context.commitChanges();

        LOGGER.info("did add mirror [{}] to repository source [{}]",
                country.getCode(), repositorySource.getCode());

        CreateRepositorySourceMirrorResult result = new CreateRepositorySourceMirrorResult();
        result.code = mirror.getCode();
        return result;
    }

    @Override
    public UpdateRepositorySourceMirrorResult updateRepositorySourceMirror(UpdateRepositorySourceMirrorRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.code), "the code for the mirror to update");

        final ObjectContext context = serverRuntime.newContext();

        RepositorySourceMirror repositorySourceMirror = RepositorySourceMirror.tryGetByCode(context, request.code)
                .orElseThrow(() -> new ObjectNotFoundException(
                        RepositorySourceMirror.class.getSimpleName(), request.code));

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                repositorySourceMirror.getRepositorySource().getRepository(),
                Permission.REPOSITORY_EDIT)) {
            throw new AuthorizationFailureException();
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
                    repositorySourceMirror.setBaseUrl(request.baseUrl);
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

        context.commitChanges();
        LOGGER.info("did update mirror [{}]", repositorySourceMirror.getCode());
        return new UpdateRepositorySourceMirrorResult();
    }

    @Override
    public GetRepositorySourceMirrorResult getRepositorySourceMirror(
            GetRepositorySourceMirrorRequest request) throws ObjectNotFoundException {

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

}
