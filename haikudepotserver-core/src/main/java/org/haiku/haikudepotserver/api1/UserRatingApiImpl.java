/*
 * Copyright 2018-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api1.model.userrating.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersion;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/userrating") // TODO; legacy path - remove
public class UserRatingApiImpl extends AbstractApiImpl implements UserRatingApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserApiImpl.class);

    private ServerRuntime serverRuntime;
    private PermissionEvaluator permissionEvaluator;
    private JobService jobService;
    private UserRatingService userRatingService;
    private PkgService pkgService;

    public UserRatingApiImpl(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            JobService jobService,
            UserRatingService userRatingService,
            PkgService pkgService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
        this.userRatingService = Preconditions.checkNotNull(userRatingService);
        this.pkgService = Preconditions.checkNotNull(pkgService);
    }

    private AbstractGetUserRatingResult.User createUser(User user) {
        Preconditions.checkNotNull(user);
        AbstractGetUserRatingResult.User result = new AbstractUserRatingResult.User();
        result.nickname = user.getNickname();
        return result;
    }

    private AbstractUserRatingResult.PkgVersion createPkgVersion(PkgVersion pkgVersion) {
        Preconditions.checkNotNull(pkgVersion);
        AbstractUserRatingResult.PkgVersion result = new AbstractUserRatingResult.PkgVersion();
        result.repositoryCode = pkgVersion.getRepositorySource().getRepository().getCode();
        result.architectureCode = pkgVersion.getArchitecture().getCode();
        result.major = pkgVersion.getMajor();
        result.minor = pkgVersion.getMinor();
        result.micro = pkgVersion.getMicro();
        result.preRelease = pkgVersion.getPreRelease();
        result.revision = pkgVersion.getRevision();
        result.pkg = new AbstractUserRatingResult.Pkg();
        result.pkg.name = pkgVersion.getPkg().getName();
        return result;
    }

    /**
     * <p>Some of the result subclass from this abstract one.  This convenience method will full the
     * abstract result with the data from the user rating so that this code does not need to be
     * repeated across multiple API methods.</p>
     */

    private void fillAbstractGetUserRatingResult(UserRating userRating, AbstractGetUserRatingResult result) {
        Preconditions.checkNotNull(userRating);
        Preconditions.checkNotNull(result);

        result.active = userRating.getActive();
        result.code = userRating.getCode();
        result.naturalLanguageCode = userRating.getNaturalLanguage().getCode();
        result.user = createUser(userRating.getUser());
        result.rating = userRating.getRating();
        result.comment = userRating.getComment();
        result.modifyTimestamp = userRating.getModifyTimestamp().getTime();
        result.createTimestamp = userRating.getCreateTimestamp().getTime();
        result.pkgVersion = createPkgVersion(userRating.getPkgVersion());

        if(null!=userRating.getUserRatingStability()) {
            result.userRatingStabilityCode = userRating.getUserRatingStability().getCode();
        }
    }

    @Override
    public DeriveAndStoreUserRatingForPkgResult deriveAndStoreUserRatingForPkg(DeriveAndStoreUserRatingForPkgRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));

        final ObjectContext context = serverRuntime.newContext();

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.USERRATING_DERIVEANDSTOREFORPKG)) {
            throw new AccessDeniedException("unable to derive and store user ratings");
        }

        Pkg.tryGetByName(context, request.pkgName).
                orElseThrow(() -> new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName));

        jobService.submit(
                new UserRatingDerivationJobSpecification(request.pkgName),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        return new DeriveAndStoreUserRatingForPkgResult();
    }

    @Override
    public DeriveAndStoreUserRatingsForAllPkgsResult deriveAndStoreUserRatingsForAllPkgs(DeriveAndStoreUserRatingsForAllPkgsResult request) {
        Preconditions.checkNotNull(request);

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.USERRATING_DERIVEANDSTOREFORPKG)) {
            throw new AccessDeniedException("unable to derive and store user ratings");
        }

        jobService.submit(
                new UserRatingDerivationJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        LOGGER.info("did enqueue request to derive and store user ratings for all packages");

        return new DeriveAndStoreUserRatingsForAllPkgsResult();
    }


    @Override
    public GetUserRatingResult getUserRating(GetUserRatingRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.code));

        final ObjectContext context = serverRuntime.newContext();

        UserRating userRating = UserRating.tryGetByCode(context, request.code).orElseThrow(
                () -> new ObjectNotFoundException(UserRating.class.getSimpleName(), request.code)
        );

        GetUserRatingResult result = new GetUserRatingResult();
        fillAbstractGetUserRatingResult(userRating, result);

        return result;
    }

    @Override
    public GetUserRatingByUserAndPkgVersionResult getUserRatingByUserAndPkgVersion(GetUserRatingByUserAndPkgVersionRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname), "the user nickname must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionArchitectureCode), "the pkg version architecture code must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionMajor),"the package version major code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository code should be supplied");

        final ObjectContext context = serverRuntime.newContext();

        Architecture architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        User user = User.tryGetByNickname(context, request.userNickname).orElseThrow(
                () -> new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname));
        Pkg pkg = Pkg.tryGetByName(context, request.pkgName).orElseThrow(
                () -> new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName));

        Repository repository = getRepository(context, request.repositoryCode);

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                request.pkgVersionMajor,
                request.pkgVersionMinor,
                request.pkgVersionMicro,
                request.pkgVersionPreRelease,
                request.pkgVersionRevision);

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                context, pkg, repository, architecture, versionCoordinates);

        if(pkgVersionOptional.isEmpty() || !pkgVersionOptional.get().getActive()) {
            throw new ObjectNotFoundException(
                    PkgVersion.class.getSimpleName(),
                    request.pkgName + "@" + versionCoordinates.toString());
        }

        Optional<UserRating> userRatingOptional = UserRating.getByUserAndPkgVersion(context, user, pkgVersionOptional.get());

        if(userRatingOptional.isEmpty()) {
            throw new ObjectNotFoundException(UserRating.class.getSimpleName(), "");
        }

        GetUserRatingByUserAndPkgVersionResult result = new GetUserRatingByUserAndPkgVersionResult();
        fillAbstractGetUserRatingResult(userRatingOptional.get(), result);

        return result;
    }


    @Override
    public CreateUserRatingResult createUserRating(CreateUserRatingRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionArchitectureCode));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository code should be supplied");
        Preconditions.checkArgument(null == request.rating || request.rating >= UserRating.MIN_USER_RATING,
                "the user rating " + request.rating + " is less than the minimum allowed of " + UserRating.MIN_USER_RATING);
        Preconditions.checkArgument(null == request.rating || request.rating <= UserRating.MAX_USER_RATING,
                "the user rating " + request.rating + " is greater than the maximum allowed of " + UserRating.MIN_USER_RATING);

        if(null!=request.comment) {
            request.comment = Strings.emptyToNull(request.comment.trim());
        }

        // check to see that the user has not tried to create a user rating with essentially nothing
        // in it.

        if(
                Strings.isNullOrEmpty(request.comment)
                        && null==request.userRatingStabilityCode
                        && null==request.rating) {
            throw new IllegalStateException("it is not possible to create a user rating with no meaningful rating data");
        }

        final ObjectContext context = serverRuntime.newContext();

        Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, request.pkgName);

        if(pkgOptional.isEmpty()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
        }

        Architecture architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);
        Repository repository = getRepository(context, request.repositoryCode);
        User user = User.tryGetByNickname(context, request.userNickname).orElseThrow(
                () -> new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname));

        Optional<UserRatingStability> userRatingStabilityOptional = Optional.empty();

        if(null!=request.userRatingStabilityCode) {
            userRatingStabilityOptional = UserRatingStability.getByCode(context, request.userRatingStabilityCode);

            if(userRatingStabilityOptional.isEmpty()) {
                throw new ObjectNotFoundException(
                        UserRatingStability.class.getSimpleName(),
                        request.userRatingStabilityCode);
            }
        }

        // check authorization

        Optional<User> authenticatedUserOptional = tryObtainAuthenticatedUser(context);

        if(authenticatedUserOptional.isEmpty()) {
            throw new AccessDeniedException("only authenticated users are able to add user ratings");
        }

        if(!authenticatedUserOptional.get().getNickname().equals(user.getNickname())) {
            throw new AccessDeniedException("it is not allowed to add a user rating for another user");
        }

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkgOptional.get(),
                Permission.PKG_CREATEUSERRATING)) {
            throw new AccessDeniedException("unable to create user ratings for [" + pkgOptional.get() + "]");
        }

        // check the package version

        Optional<PkgVersion> pkgVersionOptional;

        switch(request.pkgVersionType) {
            case LATEST:
                pkgVersionOptional = pkgService.getLatestPkgVersionForPkg(
                        context,
                        pkgOptional.get(),
                        repository,
                        Collections.singletonList(architecture));
                break;

            case SPECIFIC:
                pkgVersionOptional = PkgVersion.getForPkg(
                        context, pkgOptional.get(), repository, architecture,
                        new VersionCoordinates(
                                request.pkgVersionMajor,
                                request.pkgVersionMinor,
                                request.pkgVersionMicro,
                                request.pkgVersionPreRelease,
                                request.pkgVersionRevision));
                break;

            default:
                throw new IllegalStateException("unsupported pkg version type; " + request.pkgVersionType.name());
        }

        if(pkgVersionOptional.isEmpty()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkgOptional.get().getName()+"_"+request.pkgVersionType.name());
        }

        if(!pkgVersionOptional.get().getIsLatest()) {
            throw new IllegalStateException("it is not possible to add a user rating to a version other than the latest version.");
        }

        List<UserRating> legacyUserRatings = UserRating.findByUserAndPkg(context, user, pkgOptional.get());

        for(UserRating legacyUserRating : legacyUserRatings) {
            if(legacyUserRating.getPkgVersion().equals(pkgVersionOptional.get())) {
                throw new IllegalStateException("an existing user rating '"+legacyUserRating.getCode()+"' already exists for this package version; it is not possible to add another one");
            }
        }

        // now create the new user rating.

        UserRating userRating = context.newObject(UserRating.class);
        userRating.setCode(UUID.randomUUID().toString());
        userRating.setUserRatingStability(userRatingStabilityOptional.orElse(null));
        userRating.setUser(user);
        userRating.setComment(Strings.emptyToNull(Strings.nullToEmpty(request.comment).trim()));
        userRating.setPkgVersion(pkgVersionOptional.get());
        userRating.setNaturalLanguage(naturalLanguage);
        userRating.setRating(request.rating);

        context.commitChanges();

        LOGGER.info(
                "did create user rating for user {} on package {}",
                user.toString(),
                pkgOptional.get().toString());

        return new CreateUserRatingResult(userRating.getCode());

    }

    @Override
    public UpdateUserRatingResult updateUserRating(UpdateUserRatingRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.code));
        Preconditions.checkNotNull(request.filter);

        final ObjectContext context = serverRuntime.newContext();

        UserRating userRating = UserRating.tryGetByCode(context, request.code).orElseThrow(
                () -> new ObjectNotFoundException(UserRating.class.getSimpleName(), request.code));

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                userRating,
                Permission.USERRATING_EDIT)) {
            throw new AccessDeniedException("unable to edit the userrating");
        }

        for(UpdateUserRatingRequest.Filter filter : request.filter) {

            switch(filter) {

                case ACTIVE:
                    if(null==request.active) {
                        throw new IllegalStateException("the active flag must be supplied to configure this field");
                    }

                    userRating.setActive(request.active);
                    break;

                case COMMENT:
                    if(null!=request.comment) {
                        userRating.setComment(Strings.emptyToNull(request.comment.trim()));
                    }
                    else {
                        userRating.setComment(null);
                    }
                    break;

                case NATURALLANGUAGE:
                    NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);
                    userRating.setNaturalLanguage(naturalLanguage);
                    break;

                case RATING:
                    userRating.setRating(request.rating);
                    break;

                case USERRATINGSTABILITY:
                    if(null==request.userRatingStabilityCode) {
                        userRating.setUserRatingStability(null);
                    }
                    else {
                        userRating.setUserRatingStability(UserRatingStability.getByCode(
                                context,
                                request.userRatingStabilityCode).orElseThrow(
                                () -> new ObjectNotFoundException(
                                        UserRatingStability.class.getSimpleName(),
                                        request.userRatingStabilityCode)
                        ));
                    }
                    break;

                default:
                    throw new IllegalStateException("the filter; "+filter.name()+" is not handled");

            }

        }

        LOGGER.info(
                "did update user rating for user {} on package {}",
                userRating.getUser().toString(),
                userRating.getPkgVersion().getPkg().toString());

        context.commitChanges();

        return new UpdateUserRatingResult();
    }

    @Override
    public SearchUserRatingsResult searchUserRatings(SearchUserRatingsRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.limit);
        Preconditions.checkState(request.limit > 0);

        final ObjectContext context = serverRuntime.newContext();

        UserRatingSearchSpecification searchSpecification = new UserRatingSearchSpecification();

        if(null!=request.daysSinceCreated) {
            searchSpecification.setDaysSinceCreated(request.daysSinceCreated.intValue());
        }

        Architecture architecture = null;

        if(null!=request.pkgVersionArchitectureCode) {
            architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        }

        Optional<Pkg> pkgOptional = Optional.empty();

        if(null!=request.pkgName) {
            pkgOptional = Pkg.tryGetByName(context, request.pkgName);

            if(pkgOptional.isEmpty()) {
                throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
            }
        }

        Optional<Repository> repositoryOptional = Optional.empty();

        if(!Strings.isNullOrEmpty(request.repositoryCode)) {
            searchSpecification.setRepository(getRepository(
                    context,
                    request.repositoryCode));
        }

        // if there is a major version specified then we must be requesting a specific package version,
        // otherwise we will constrain based on the architecture and/or the package name.

        if (null != request.pkgVersionMajor) {

            if (repositoryOptional.isEmpty()) {
                throw new IllegalStateException("the repository is required when a pkg version is specified");
            }

            if (pkgOptional.isEmpty()) {
                throw new IllegalStateException("the pkg is required when a pkg version is specified");
            }

            if (null == architecture) {
                throw new IllegalStateException("the architecture is required when a pkg version is specified");
            }

            PkgVersion pkgVersion = PkgVersion.getForPkg(
                    context,
                    pkgOptional.get(),
                    repositoryOptional.get(),
                    architecture,
                    new VersionCoordinates(
                            request.pkgVersionMajor,
                            request.pkgVersionMinor,
                            request.pkgVersionMicro,
                            request.pkgVersionPreRelease,
                            request.pkgVersionRevision))
                    .filter(_PkgVersion::getActive)
                    .orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(),""));

            searchSpecification.setPkgVersion(pkgVersion);
        }
        else {
            searchSpecification.setArchitecture(architecture);

            if(pkgOptional.isPresent()) {
                searchSpecification.setPkg(pkgOptional.get());
            }
        }

        if(null!=request.userNickname) {
            Optional<User> userOptional = User.tryGetByNickname(context, request.userNickname);

            if(userOptional.isEmpty()) {
                throw new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname);
            }

            searchSpecification.setUser(userOptional.get());
        }

        searchSpecification.setLimit(request.limit);
        searchSpecification.setOffset(request.offset);

        List<UserRating> foundUserRatings = userRatingService.search(context, searchSpecification);

        final SearchUserRatingsResult result = new SearchUserRatingsResult();
        result.total = userRatingService.total(context, searchSpecification);
        result.items = foundUserRatings
                .stream()
                .map(ur -> {
                    SearchUserRatingsResult.UserRating resultUserRating = new SearchUserRatingsResult.UserRating();
                    resultUserRating.active = ur.getActive();
                    resultUserRating.code = ur.getCode();
                    resultUserRating.comment = ur.getComment();
                    resultUserRating.createTimestamp = ur.getCreateTimestamp().getTime();
                    resultUserRating.modifyTimestamp = ur.getModifyTimestamp().getTime();
                    resultUserRating.userRatingStabilityCode = null!=ur.getUserRatingStability() ? ur.getUserRatingStability().getCode() : null;
                    resultUserRating.naturalLanguageCode = ur.getNaturalLanguage().getCode();
                    resultUserRating.pkgVersion = createPkgVersion(ur.getPkgVersion());
                    resultUserRating.rating = ur.getRating();
                    resultUserRating.user = createUser(ur.getUser());
                    return resultUserRating;
                })
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public RemoveUserRatingResult removeUserRating(RemoveUserRatingRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(StringUtils.isNotBlank(request.code));

        final ObjectContext context = serverRuntime.newContext();

        UserRating userRating = UserRating.tryGetByCode(context, request.code).orElseThrow(
                () -> new ObjectNotFoundException(UserRating.class.getSimpleName(), request.code));

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                userRating, Permission.USERRATING_REMOVE)) {
            throw new AccessDeniedException("unable to delete the userrating");
        }

        userRatingService.removeUserRatingAtomically(userRating.getCode());

        return new RemoveUserRatingResult();
    }

}
