/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.repository.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.api1.support.ValidationException;
import org.haikuos.haikudepotserver.api1.support.ValidationFailure;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.pkg.model.PkgRepositoryImportJob;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.repository.RepositoryImportService;
import org.haikuos.haikudepotserver.repository.RepositoryService;
import org.haikuos.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.naming.ldap.PagedResultsControl;
import java.util.List;

@Component
public class RepositoryApiImpl extends AbstractApiImpl implements RepositoryApi {

    protected static Logger logger = LoggerFactory.getLogger(RepositoryApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthorizationService authorizationService;

    @Resource
    RepositoryService repositoryService;

    @Resource
    RepositoryImportService repositoryImportService;

    // note; no integration test for this one.
    @Override
    public TriggerImportRepositoryResult triggerImportRepository(
            TriggerImportRepositoryRequest triggerImportRepositoryRequest)
            throws ObjectNotFoundException {

        Preconditions.checkNotNull(triggerImportRepositoryRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(triggerImportRepositoryRequest.code));

        final ObjectContext context = serverRuntime.getContext();

        Optional<Repository> repositoryOptional = Repository.getByCode(context, triggerImportRepositoryRequest.code);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), triggerImportRepositoryRequest.code);
        }

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orNull(),
                repositoryOptional.get(),
                Permission.REPOSITORY_IMPORT)) {
            throw new AuthorizationFailureException();
        }

        repositoryImportService.submit(new PkgRepositoryImportJob(repositoryOptional.get().getCode()));

        return new TriggerImportRepositoryResult();
    }


    @Override
    public SearchRepositoriesResult searchRepositories(SearchRepositoriesRequest request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.getContext();

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orNull(),
                null,
                Permission.REPOSITORY_LIST)) {
            throw new AuthorizationFailureException();
        }

        if(null!=request.includeInactive && request.includeInactive) {
            if(!authorizationService.check(
                    context,
                    tryObtainAuthenticatedUser(context).orNull(),
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

        specification.setLimit(request.limit+1); // get +1 to see if there are any more.
        specification.setOffset(request.offset);
        specification.setIncludeInactive(null!=request.includeInactive && request.includeInactive);

        SearchRepositoriesResult result = new SearchRepositoriesResult();
        List<Repository> searchedRepositories = repositoryService.search(context,specification);

        // if there are more than we asked for then there must be more available.

        result.hasMore = new Boolean(searchedRepositories.size() > request.limit);

        if(result.hasMore) {
            searchedRepositories = searchedRepositories.subList(0,request.limit);
        }

        result.items = Lists.newArrayList(Iterables.transform(
                searchedRepositories,
                new Function<Repository, SearchRepositoriesResult.Repository>() {
                    @Override
                    public SearchRepositoriesResult.Repository apply(Repository input) {
                        SearchRepositoriesResult.Repository resultRepository = new SearchRepositoriesResult.Repository();
                        resultRepository.active = input.getActive();
                        resultRepository.architectureCode = input.getArchitecture().getCode();
                        resultRepository.code = input.getCode();
                        return resultRepository;
                    }
                }
        ));

        return result;
    }

    @Override
    public GetRepositoryResult getRepository(GetRepositoryRequest getRepositoryRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getRepositoryRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getRepositoryRequest.code));

        final ObjectContext context = serverRuntime.getContext();

        Optional<Repository> repositoryOptional = Repository.getByCode(context, getRepositoryRequest.code);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), getRepositoryRequest.code);
        }

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orNull(),
                repositoryOptional.get(),
                Permission.REPOSITORY_VIEW)) {
            throw new AuthorizationFailureException();
        }

        GetRepositoryResult result = new GetRepositoryResult();
        result.active = repositoryOptional.get().getActive();
        result.architectureCode = repositoryOptional.get().getArchitecture().getCode();
        result.code = repositoryOptional.get().getCode();
        result.createTimestamp = repositoryOptional.get().getCreateTimestamp().getTime();
        result.modifyTimestamp = repositoryOptional.get().getModifyTimestamp().getTime();
        result.url = repositoryOptional.get().getUrl();

        return result;
    }

    @Override
    public UpdateRepositoryResult updateRepository(UpdateRepositoryRequest updateRepositoryRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(updateRepositoryRequest);

        final ObjectContext context = serverRuntime.getContext();

        Optional<Repository> repositoryOptional = Repository.getByCode(context, updateRepositoryRequest.code);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), updateRepositoryRequest.code);
        }

        authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orNull(),
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
                        logger.info("did set the active flag on repository {} to {}", updateRepositoryRequest.code,updateRepositoryRequest.active);
                    }

                    break;

                case URL:
                    repositoryOptional.get().setUrl(updateRepositoryRequest.url);
                    logger.info("did set the url on repository {} to {}", updateRepositoryRequest.code,updateRepositoryRequest.url);
                    break;

                default:
                    throw new IllegalStateException("unhandled filter for updating a repository");
            }
        }

        if(context.hasChanges()) {
            context.commitChanges();
        }
        else {
            logger.info("update repository {} with no changes made", updateRepositoryRequest.code);
        }

        return new UpdateRepositoryResult();
    }

    @Override
    public CreateRepositoryResult createRepository(
            CreateRepositoryRequest createRepositoryRequest)
            throws ObjectNotFoundException {

        Preconditions.checkNotNull(createRepositoryRequest);

        final ObjectContext context = serverRuntime.getContext();

        authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orNull(),
                null,
                Permission.REPOSITORY_CREATE);

        Optional<Architecture> architectureOptional = Architecture.getByCode(context, createRepositoryRequest.architectureCode);

        if(!architectureOptional.isPresent()) {
            throw new ObjectNotFoundException(Architecture.class.getSimpleName(), createRepositoryRequest.architectureCode);
        }

        // the code must be supplied.

        if(Strings.isNullOrEmpty(createRepositoryRequest.code)) {
            throw new ValidationException(new ValidationFailure(Repository.CODE_PROPERTY, "required"));
        }

        // check to see if there is an existing repository with the same code; non-unique.

        {
            Optional<Repository> repositoryOptional = Repository.getByCode(context, createRepositoryRequest.code);

            if(repositoryOptional.isPresent()) {
                throw new ValidationException(new ValidationFailure(Repository.CODE_PROPERTY, "unique"));
            }
        }

        Repository repository = context.newObject(Repository.class);

        repository.setCode(createRepositoryRequest.code);
        repository.setActive(Boolean.TRUE);
        repository.setUrl(createRepositoryRequest.url);
        repository.setArchitecture(architectureOptional.get());

        context.commitChanges();

        return new CreateRepositoryResult();
    }


}
