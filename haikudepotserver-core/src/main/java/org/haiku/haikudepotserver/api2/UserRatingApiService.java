/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.api2.model.CreateUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateUserRatingResult;
import org.haiku.haikudepotserver.api2.model.DeriveAndStoreUserRatingForPkgRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionPkg;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionPkgVersion;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionResult;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionUser;
import org.haiku.haikudepotserver.api2.model.GetUserRatingPkg;
import org.haiku.haikudepotserver.api2.model.GetUserRatingPkgVersion;
import org.haiku.haikudepotserver.api2.model.GetUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingResult;
import org.haiku.haikudepotserver.api2.model.GetUserRatingUser;
import org.haiku.haikudepotserver.api2.model.RemoveUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsPkg;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsPkgVersion;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsResult;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsResultItems;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsUser;
import org.haiku.haikudepotserver.api2.model.UpdateUserRatingFilter;
import org.haiku.haikudepotserver.api2.model.UpdateUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserRating;
import org.haiku.haikudepotserver.dataobjects.UserRatingStability;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component("userRatingApiServiceV2")
public class UserRatingApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingApiService.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final JobService jobService;
    private final UserRatingService userRatingService;
    private final PkgService pkgService;

    public UserRatingApiService(
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

    public CreateUserRatingResult createUserRating(CreateUserRatingRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNaturalLanguageCode()));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgVersionArchitectureCode()));
        // temporary support the `repositoryCode` for the desktop client.
        Preconditions.checkArgument(
                Stream.of(request.getPkgVersionArchitectureCode(), request.getRepositoryCode())
                        .map(StringUtils::trimToNull)
                        .filter(Objects::nonNull)
                        .count() == 2 || StringUtils.isNotBlank(request.getRepositorySourceCode()),
                "the (repository code and architecture) are required or the repository source code");
        Preconditions.checkArgument(null == request.getRating() || request.getRating() >= UserRating.MIN_USER_RATING,
                "the user rating " + request.getRating() + " is less than the minimum allowed of " + UserRating.MIN_USER_RATING);
        Preconditions.checkArgument(null == request.getRating() || request.getRating() <= UserRating.MAX_USER_RATING,
                "the user rating " + request.getRating() + " is greater than the maximum allowed of " + UserRating.MIN_USER_RATING);

        String comment = StringUtils.trimToNull(request.getComment());

        // check to see that the user has not tried to create a user rating with essentially nothing
        // in it.

        if(
                Strings.isNullOrEmpty(request.getComment())
                        && null == request.getUserRatingStabilityCode()
                        && null == request.getRating()) {
            throw new IllegalStateException("it is not possible to create a user rating with no meaningful rating data");
        }

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        // because there are some older HD desktop clients that will still come
        // through with architecture + repository, we can work with this to
        // probably get the repository source.

        Architecture architecture = getArchitecture(context, request.getPkgVersionArchitectureCode());
        RepositorySource repositorySource;

        if (StringUtils.isNotBlank(request.getRepositorySourceCode())) {
            repositorySource = getRepositorySource(context, request.getRepositorySourceCode());
        } else {
            repositorySource = getRepository(context, request.getRepositoryCode())
                    .tryGetRepositorySourceForArchitecture(architecture)
                    .orElseThrow(() -> new ObjectNotFoundException(RepositorySource.class.getSimpleName(), ""));
        }

        NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.getNaturalLanguageCode());
        User user = User.tryGetByNickname(context, request.getUserNickname()).orElseThrow(
                () -> new ObjectNotFoundException(User.class.getSimpleName(), request.getUserNickname()));

        Optional<UserRatingStability> userRatingStabilityOptional = Optional.empty();

        if(null!=request.getUserRatingStabilityCode()) {
            userRatingStabilityOptional = UserRatingStability.tryGetByCode(context, request.getUserRatingStabilityCode());

            if(userRatingStabilityOptional.isEmpty()) {
                throw new ObjectNotFoundException(
                        UserRatingStability.class.getSimpleName(),
                        request.getUserRatingStabilityCode());
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
                pkg, Permission.PKG_CREATEUSERRATING)) {
            throw new AccessDeniedException("unable to create user ratings for [" + pkg + "]");
        }

        // check the package version

        Optional<PkgVersion> pkgVersionOptional;

        switch(request.getPkgVersionType()) {
            case LATEST:
                pkgVersionOptional = pkgService.getLatestPkgVersionForPkg(
                        context,
                        pkg,
                        repositorySource,
                        Collections.singletonList(architecture));
                break;

            case SPECIFIC:
                pkgVersionOptional = PkgVersion.tryGetForPkg(
                        context, pkg, repositorySource, architecture,
                        new VersionCoordinates(
                                request.getPkgVersionMajor(),
                                request.getPkgVersionMinor(),
                                request.getPkgVersionMicro(),
                                request.getPkgVersionPreRelease(),
                                request.getPkgVersionRevision()));
                break;

            default:
                throw new IllegalStateException("unsupported pkg version type; " + request.getPkgVersionType());
        }

        if(pkgVersionOptional.isEmpty()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName()+"_"+request.getPkgVersionType());
        }

        if(!pkgVersionOptional.get().getIsLatest()) {
            throw new IllegalStateException("it is not possible to add a user rating to a version other than the latest version.");
        }

        List<UserRating> legacyUserRatings = UserRating.findByUserAndPkg(context, user, pkg);

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
        userRating.setComment(Strings.emptyToNull(Strings.nullToEmpty(comment).trim()));
        userRating.setPkgVersion(pkgVersionOptional.get());
        userRating.setNaturalLanguage(naturalLanguage);
        userRating.setRating(Optional.ofNullable(request.getRating()).map(Number::shortValue).orElse(null));

        context.commitChanges();

        LOGGER.info("did create user rating for user {} on package {}", user, pkg);

        return new CreateUserRatingResult().code(userRating.getCode());
    }

    public void deriveAndStoreUserRatingForPkg(DeriveAndStoreUserRatingForPkgRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()));

        final ObjectContext context = serverRuntime.newContext();

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.USERRATING_DERIVEANDSTOREFORPKG)) {
            throw new AccessDeniedException("unable to derive and store user ratings");
        }

        Pkg.tryGetByName(context, request.getPkgName())
            .orElseThrow(() -> new ObjectNotFoundException(Pkg.class.getSimpleName(), request.getPkgName()));

        jobService.submit(
                new UserRatingDerivationJobSpecification(request.getPkgName()),
                JobSnapshot.COALESCE_STATUSES_QUEUED);
    }

    public void deriveAndStoreUserRatingsForAllPkgs() {
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
    }

    public GetUserRatingResult getUserRating(GetUserRatingRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getCode()));

        final ObjectContext context = serverRuntime.newContext();
        UserRating userRating = UserRating.getByCode(context, request.getCode());
        PkgVersion pkgVersion = userRating.getPkgVersion();
        Pkg pkg = pkgVersion.getPkg();

        return new GetUserRatingResult()
                .code(userRating.getCode())
                .naturalLanguageCode(userRating.getNaturalLanguage().getCode())
                .user(new GetUserRatingUser().nickname(userRating.getUser().getNickname()))
                .userRatingStabilityCode(userRating.getUserRatingStability().getCode())
                .active(userRating.getActive())
                .comment(userRating.getComment())
                .modifyTimestamp(userRating.getModifyTimestamp().getTime())
                .createTimestamp(userRating.getCreateTimestamp().getTime())
                .rating(Optional.ofNullable(userRating.getRating()).map(Number::intValue).orElse(null))
                .pkgVersion(new GetUserRatingPkgVersion()
                        .repositoryCode(pkgVersion.getRepositorySource().getRepository().getCode())
                        .architectureCode(pkgVersion.getArchitecture().getCode())
                        .major(pkgVersion.getMajor())
                        .minor(pkgVersion.getMinor())
                        .micro(pkgVersion.getMicro())
                        .preRelease(pkgVersion.getPreRelease())
                        .revision(pkgVersion.getRevision())
                        .pkg(new GetUserRatingPkg().name(pkg.getName())));
    }

    public GetUserRatingByUserAndPkgVersionResult getUserRatingByUserAndPkgVersion(GetUserRatingByUserAndPkgVersionRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()), "the package name must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getUserNickname()), "the user nickname must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgVersionArchitectureCode()), "the pkg version architecture code must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgVersionMajor()),"the package version major code must be supplied");

        // temporary support the `repositoryCode` for the desktop client.
        Preconditions.checkArgument(
                Stream.of(request.getPkgVersionArchitectureCode(), request.getRepositoryCode())
                        .map(StringUtils::trimToNull)
                        .filter(Objects::nonNull)
                        .count() == 2 || StringUtils.isNotBlank(request.getRepositorySourceCode()),
                "the (repository code and architecture) are required or the repository source code");

        final ObjectContext context = serverRuntime.newContext();

        Architecture architecture = getArchitecture(context, request.getPkgVersionArchitectureCode());
        User user = User.getByNickname(context, request.getUserNickname());
        Pkg pkg = getPkg(context, request.getPkgName());

        // because there are some older HD desktop clients that will still come
        // through with architecture + repository, we can work with this to
        // probably get the repository source.

        RepositorySource repositorySource;

        if (StringUtils.isNotBlank(request.getRepositorySourceCode())) {
            repositorySource = getRepositorySource(context, request.getRepositorySourceCode());
        } else {
            repositorySource = getRepository(context, request.getRepositoryCode())
                    .tryGetRepositorySourceForArchitecture(architecture)
                    .orElseThrow(() -> new ObjectNotFoundException(RepositorySource.class.getSimpleName(), ""));
        }

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                request.getPkgVersionMajor(),
                request.getPkgVersionMinor(),
                request.getPkgVersionMicro(),
                request.getPkgVersionPreRelease(),
                request.getPkgVersionRevision());

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.tryGetForPkg(
                context, pkg, repositorySource, architecture, versionCoordinates);

        if(pkgVersionOptional.isEmpty() || !pkgVersionOptional.get().getActive()) {
            throw new ObjectNotFoundException(
                    PkgVersion.class.getSimpleName(),
                    request.getPkgName() + "@" + versionCoordinates);
        }

        UserRating userRating = UserRating.getByUserAndPkgVersion(context, user, pkgVersionOptional.get())
                .orElseThrow(() -> new ObjectNotFoundException(UserRating.class.getSimpleName(), ""));
        PkgVersion pkgVersion = userRating.getPkgVersion();

        return new GetUserRatingByUserAndPkgVersionResult()
                .code(userRating.getCode())
                .naturalLanguageCode(userRating.getNaturalLanguage().getCode())
                .user(new GetUserRatingByUserAndPkgVersionUser().nickname(userRating.getUser().getNickname()))
                .userRatingStabilityCode(userRating.getUserRatingStability().getCode())
                .active(userRating.getActive())
                .comment(userRating.getComment())
                .modifyTimestamp(userRating.getModifyTimestamp().getTime())
                .createTimestamp(userRating.getCreateTimestamp().getTime())
                .rating(Optional.ofNullable(userRating.getRating()).map(Number::intValue).orElse(null))
                .pkgVersion(new GetUserRatingByUserAndPkgVersionPkgVersion()
                        .repositoryCode(pkgVersion.getRepositorySource().getRepository().getCode())
                        .architectureCode(pkgVersion.getArchitecture().getCode())
                        .major(pkgVersion.getMajor())
                        .minor(pkgVersion.getMinor())
                        .micro(pkgVersion.getMicro())
                        .preRelease(pkgVersion.getPreRelease())
                        .revision(pkgVersion.getRevision())
                        .pkg(new GetUserRatingByUserAndPkgVersionPkg().name(pkg.getName())));
    }

    public void removeUserRating(RemoveUserRatingRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(StringUtils.isNotBlank(request.getCode()));

        final ObjectContext context = serverRuntime.newContext();

        UserRating userRating = UserRating.tryGetByCode(context, request.getCode()).orElseThrow(
                () -> new ObjectNotFoundException(UserRating.class.getSimpleName(), request.getCode()));

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                userRating, Permission.USERRATING_REMOVE)) {
            throw new AccessDeniedException("unable to delete the userrating");
        }

        userRatingService.removeUserRatingAtomically(userRating.getCode());
    }

    public SearchUserRatingsResult searchUserRatings(SearchUserRatingsRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.getLimit());
        Preconditions.checkState(request.getLimit() > 0);

        final ObjectContext context = serverRuntime.newContext();

        UserRatingSearchSpecification searchSpecification = new UserRatingSearchSpecification();

        if (null != request.getDaysSinceCreated()) {
            searchSpecification.setDaysSinceCreated(request.getDaysSinceCreated());
        }

        Architecture architecture = null;

        if (null != request.getPkgVersionArchitectureCode()) {
            architecture = getArchitecture(context, request.getPkgVersionArchitectureCode());
        }

        Pkg pkg = null;

        if (null != request.getPkgName()) {
            pkg = Pkg.getByName(context, request.getPkgName());
        }

        RepositorySource repositorySource = Optional.ofNullable(request.getRepositorySourceCode())
                .map(StringUtils::trimToNull)
                .map(rsc -> getRepositorySource(context, rsc))
                .orElse(null);
        searchSpecification.setRepositorySource(repositorySource);

        // this is temporarily here to support the desktop application; remove
        // when that version of the client is no longer supported.
        Repository repository = Optional.ofNullable(request.getRepositoryCode())
                .map(StringUtils::trimToNull)
                .map(rc -> getRepository(context, rc))
                .orElse(null);
        searchSpecification.setRepository(repository);

        // if there is a major version specified then we must be requesting a specific package version,
        // otherwise we will constrain based on the architecture and/or the package name.

        if (null != request.getPkgVersionMajor()) {

            if (null == repositorySource) {
                throw new IllegalStateException("the repository source is required when a pkg version is specified");
            }

            if (null == pkg) {
                throw new IllegalStateException("the pkg is required when a pkg version is specified");
            }

            if (null == architecture) {
                throw new IllegalStateException("the architecture is required when a pkg version is specified");
            }

            PkgVersion pkgVersion = PkgVersion.tryGetForPkg(
                            context,
                            pkg,
                            repositorySource,
                            architecture,
                            new VersionCoordinates(
                                    request.getPkgVersionMajor(),
                                    request.getPkgVersionMinor(),
                                    request.getPkgVersionMicro(),
                                    request.getPkgVersionPreRelease(),
                                    request.getPkgVersionRevision()))
                    .filter(_PkgVersion::getActive)
                    .orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(),""));

            searchSpecification.setPkgVersion(pkgVersion);
        }
        else {
            searchSpecification.setArchitecture(architecture);
            searchSpecification.setPkg(pkg);
        }

        if (null != request.getUserNickname()) {
            searchSpecification.setUser(User.getByNickname(context, request.getUserNickname()));
        }

        searchSpecification.setLimit(request.getLimit());
        searchSpecification.setOffset(request.getOffset());

        long total = userRatingService.total(context, searchSpecification);
        List<SearchUserRatingsResultItems> items = List.of();

        if (total > 0) {
            items = userRatingService.search(context, searchSpecification)
                    .stream()
                    .map(ur -> new SearchUserRatingsResultItems()
                            .active(ur.getActive())
                            .code(ur.getCode())
                            .comment(ur.getComment())
                            .createTimestamp(ur.getCreateTimestamp().getTime())
                            .modifyTimestamp(ur.getModifyTimestamp().getTime())
                            .userRatingStabilityCode(
                                    Optional.ofNullable(ur.getUserRatingStability())
                                            .map(UserRatingStability::getCode)
                                            .orElse(null))
                            .naturalLanguageCode(ur.getNaturalLanguage().getCode())
                            .rating(Optional.ofNullable(ur.getRating()).map(Number::intValue).orElse(null))
                            .user(new SearchUserRatingsUser().nickname(ur.getUser().getNickname()))
                            .pkgVersion(new SearchUserRatingsPkgVersion()
                                    .repositoryCode(ur.getPkgVersion().getRepositorySource().getRepository().getCode())
                                    .repositorySourceCode(ur.getPkgVersion().getRepositorySource().getCode())
                                    .architectureCode(ur.getPkgVersion().getArchitecture().getCode())
                                    .major(ur.getPkgVersion().getMajor())
                                    .minor(ur.getPkgVersion().getMinor())
                                    .micro(ur.getPkgVersion().getMicro())
                                    .preRelease(ur.getPkgVersion().getPreRelease())
                                    .revision(ur.getPkgVersion().getRevision())
                                    .pkg(new SearchUserRatingsPkg().name(ur.getPkgVersion().getPkg().getName()))))
                    .collect(Collectors.toUnmodifiableList());
        }

        return new SearchUserRatingsResult()
                .total(total)
                .items(items);
    }

    public void updateUserRating(UpdateUserRatingRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getCode()));
        Preconditions.checkNotNull(request.getFilter());

        final ObjectContext context = serverRuntime.newContext();

        UserRating userRating = UserRating.getByCode(context, request.getCode());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                userRating,
                Permission.USERRATING_EDIT)) {
            throw new AccessDeniedException("unable to edit the userrating");
        }

        for (UpdateUserRatingFilter filter : request.getFilter()) {

            switch(filter) {

                case ACTIVE:
                    if (null == request.getActive()) {
                        throw new IllegalStateException("the active flag must be supplied to configure this field");
                    }

                    userRating.setActive(request.getActive());
                    break;

                case COMMENT:
                    userRating.setComment(StringUtils.trimToNull(request.getComment()));
                    break;

                case NATURALLANGUAGE:
                    userRating.setNaturalLanguage(getNaturalLanguage(context, request.getNaturalLanguageCode()));
                    break;

                case RATING:
                    userRating.setRating(Optional.ofNullable(request.getRating()).map(Number::shortValue).orElse(null));
                    break;

                case USERRATINGSTABILITY:
                        userRating.setUserRatingStability(
                                Optional.ofNullable(request.getUserRatingStabilityCode())
                                                .map(urc -> UserRatingStability.getByCode(context, urc))
                                                        .orElse(null));
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

    }

}
