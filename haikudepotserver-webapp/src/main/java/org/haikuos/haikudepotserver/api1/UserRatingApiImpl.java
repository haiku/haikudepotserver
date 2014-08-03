/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.userrating.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.haikuos.haikudepotserver.userrating.UserRatingOrchestrationService;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class UserRatingApiImpl extends AbstractApiImpl implements UserRatingApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthorizationService authorizationService;

    @Resource
    UserRatingOrchestrationService userRatingOrchestrationService;

    @Resource
    PkgOrchestrationService pkgOrchestrationService;

    private AbstractGetUserRatingResult.User createUser(User user) {
        Preconditions.checkNotNull(user);
        AbstractGetUserRatingResult.User result = new AbstractUserRatingResult.User();
        result.nickname = user.getNickname();
        return result;
    }

    private AbstractUserRatingResult.PkgVersion createPkgVersion(PkgVersion pkgVersion) {
        Preconditions.checkNotNull(pkgVersion);
        AbstractUserRatingResult.PkgVersion result = new AbstractUserRatingResult.PkgVersion();
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
    public GetUserRatingResult getUserRating(GetUserRatingRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.code));

        final ObjectContext context = serverRuntime.getContext();

        Optional<UserRating> userRatingOptional = UserRating.getByCode(context, request.code);

        if(!userRatingOptional.isPresent()) {
            throw new ObjectNotFoundException(UserRating.class.getSimpleName(), request.code);
        }

        GetUserRatingResult result = new GetUserRatingResult();
        fillAbstractGetUserRatingResult(userRatingOptional.get(), result);

        return result;
    }

    @Override
    public GetUserRatingByUserAndPkgVersionResult getUserRatingByUserAndPkgVersion(GetUserRatingByUserAndPkgVersionRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname), "the user nickname must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionArchitectureCode), "the pkg version architecture code must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionMajor),"the package version major code must be supplied");

        final ObjectContext context = serverRuntime.getContext();

        Architecture architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        Optional<User> userOptional = User.getByNickname(context, request.userNickname);

        if(!userOptional.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname);
        }

        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
        }

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                request.pkgVersionMajor,
                request.pkgVersionMinor,
                request.pkgVersionMicro,
                request.pkgVersionPreRelease,
                request.pkgVersionRevision);

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                context,
                pkgOptional.get(),
                architecture,
                versionCoordinates);

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(
                    PkgVersion.class.getSimpleName(),
                    request.pkgName + "@" + versionCoordinates.toString());
        }

        Optional<UserRating> userRatingOptional = UserRating.getByUserAndPkgVersion(context, userOptional.get(), pkgVersionOptional.get());

        if(!userRatingOptional.isPresent()) {
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
        Preconditions.checkNotNull(null!=request.pkgVersionType);

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

        final ObjectContext context = serverRuntime.getContext();

        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
        }

        Architecture architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);

        Optional<User> userOptional = User.getByNickname(context, request.userNickname);

        if(!userOptional.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname);
        }

        Optional<UserRatingStability> userRatingStabilityOptional = Optional.absent();

        if(null!=request.userRatingStabilityCode) {
            userRatingStabilityOptional = UserRatingStability.getByCode(context, request.userRatingStabilityCode);

            if(!userRatingStabilityOptional.isPresent()) {
                throw new ObjectNotFoundException(
                        UserRatingStability.class.getSimpleName(),
                        request.userRatingStabilityCode);
            }
        }

        // check authorization

        Optional<User> authenticatedUserOptional = tryObtainAuthenticatedUser(context);

        if(!authenticatedUserOptional.isPresent()) {
            LOGGER.warn("only authenticated users are able to add user ratings");
            throw new AuthorizationFailureException();
        }

        if(!authenticatedUserOptional.get().getNickname().equals(userOptional.get().getNickname())) {
            LOGGER.warn("it is not allowed to add a user rating for another user");
            throw new AuthorizationFailureException();
        }

        if(!authorizationService.check(
                context,
                authenticatedUserOptional.orNull(),
                pkgOptional.get(),
                Permission.PKG_CREATEUSERRATING)) {
            throw new AuthorizationFailureException();
        }

        // check the package version

        Optional<PkgVersion> pkgVersionOptional;

        switch(request.pkgVersionType) {
            case LATEST:
                pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                        context,
                        pkgOptional.get(),
                        Collections.singletonList(architecture));
                break;

            default:
                throw new IllegalStateException("unsupported pkg version type; " + request.pkgVersionType.name());
        }

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkgOptional.get().getName()+"_"+request.pkgVersionType.name());
        }

        if(!pkgVersionOptional.get().getIsLatest()) {
            throw new IllegalStateException("it is not possible to add a user rating to a version other than the latest version.");
        }

        List<UserRating> legacyUserRatings = UserRating.findByUserAndPkg(context, userOptional.get(), pkgOptional.get());

        for(UserRating legacyUserRating : legacyUserRatings) {
            if(legacyUserRating.getPkgVersion().equals(pkgVersionOptional.get())) {
                throw new IllegalStateException("an exisiting user rating '"+legacyUserRating.getCode()+"' already exists for this package version; it is not possible to add another one");
            }
        }

        // now create the new user rating.

        UserRating userRating = context.newObject(UserRating.class);
        userRating.setCode(UUID.randomUUID().toString());
        userRating.setUserRatingStability(userRatingStabilityOptional.orNull());
        userRating.setUser(userOptional.get());
        userRating.setComment(Strings.emptyToNull(Strings.nullToEmpty(request.comment).trim()));
        userRating.setPkgVersion(pkgVersionOptional.get());
        userRating.setNaturalLanguage(naturalLanguage);
        userRating.setRating(request.rating);

        context.commitChanges();

        LOGGER.info(
                "did create user rating for user {} on package {}",
                userOptional.get().toString(),
                pkgOptional.get().toString());

        return new CreateUserRatingResult(userRating.getCode());

    }

    @Override
    public UpdateUserRatingResult updateUserRating(UpdateUserRatingRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.code));
        Preconditions.checkNotNull(request.filter);

        final ObjectContext context = serverRuntime.getContext();

        Optional<UserRating> userRatingOptional = UserRating.getByCode(context, request.code);

        if(!userRatingOptional.isPresent()) {
            throw new ObjectNotFoundException(UserRating.class.getSimpleName(), request.code);
        }

        User authenticatedUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(
                context,
                authenticatedUser,
                userRatingOptional.get(),
                Permission.USERRATING_EDIT)) {
            LOGGER.error("unable to edit the userrating as {}", authenticatedUser.toString());
            throw new AuthorizationFailureException();
        }

        for(UpdateUserRatingRequest.Filter filter : request.filter) {

            switch(filter) {

                case ACTIVE:
                    if(null==request.active) {
                        throw new IllegalStateException("the active flag must be supplied to configure this field");
                    }

                    userRatingOptional.get().setActive(request.active);
                    break;

                case COMMENT:
                    if(null!=request.comment) {
                        userRatingOptional.get().setComment(Strings.emptyToNull(request.comment.trim()));
                    }
                    else {
                        userRatingOptional.get().setComment(null);
                    }
                    break;

                case NATURALLANGUAGE:
                    NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);
                    userRatingOptional.get().setNaturalLanguage(naturalLanguage);
                    break;

                case RATING:
                    userRatingOptional.get().setRating(request.rating);
                    break;

                case USERRATINGSTABILITY:
                    if(null==request.userRatingStabilityCode) {
                        userRatingOptional.get().setUserRatingStability(null);
                    }
                    else {
                        Optional<UserRatingStability> userRatingStabilityOptional = UserRatingStability.getByCode(
                                context,
                                request.userRatingStabilityCode);

                        if (!userRatingStabilityOptional.isPresent()) {
                            throw new ObjectNotFoundException(
                                    UserRatingStability.class.getSimpleName(),
                                    request.userRatingStabilityCode);
                        }

                        userRatingOptional.get().setUserRatingStability(userRatingStabilityOptional.get());
                    }
                    break;

                default:
                    throw new IllegalStateException("the filter; "+filter.name()+" is not handled");

            }

        }

        LOGGER.info(
                "did update user rating for user {} on package {}",
                userRatingOptional.get().getUser().toString(),
                userRatingOptional.get().getPkgVersion().getPkg().toString());

        context.commitChanges();

        return new UpdateUserRatingResult();
    }

    @Override
    public SearchUserRatingsResult searchUserRatings(SearchUserRatingsRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.limit);
        Preconditions.checkState(request.limit > 0);

        final ObjectContext context = serverRuntime.getContext();

        UserRatingSearchSpecification searchSpecification = new UserRatingSearchSpecification();

        if(null!=request.daysSinceCreated) {
            searchSpecification.setDaysSinceCreated(request.daysSinceCreated.intValue());
        }

        Architecture architecture = null;

        if(null!=request.pkgVersionArchitectureCode) {
            architecture = getArchitecture(context, request.pkgVersionArchitectureCode);
        }

        Optional<Pkg> pkgOptional = Optional.absent();

        if(null!=request.pkgName) {
            pkgOptional = Pkg.getByName(context, request.pkgName);

            if(!pkgOptional.isPresent()) {
                throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
            }
        }

        // if there is a major version specified then we must be requesting a specific package version,
        // otherwise we will constrain based on the architecture and/or the package name.

        if(null!=request.pkgVersionMajor) {

            if(!pkgOptional.isPresent()) {
                throw new IllegalStateException("the pkg is required when a pkg version is specified");
            }

            if(null==architecture) {
                throw new IllegalStateException("the architecture is required when a pkg version is specified");
            }

            Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                    context,
                    pkgOptional.get(),
                    architecture,
                    new VersionCoordinates(
                            request.pkgVersionMajor,
                            request.pkgVersionMinor,
                            request.pkgVersionMicro,
                            request.pkgVersionPreRelease,
                            request.pkgVersionRevision));

            if(!pkgVersionOptional.isPresent()) {
                throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(),"");
            }

            searchSpecification.setPkgVersion(pkgVersionOptional.get());
        }
        else {
            searchSpecification.setArchitecture(architecture);

            if(pkgOptional.isPresent()) {
                searchSpecification.setPkg(pkgOptional.get());
            }
        }

        if(null!=request.userNickname) {
            Optional<User> userOptional = User.getByNickname(context, request.userNickname);

            if(!userOptional.isPresent()) {
                throw new ObjectNotFoundException(User.class.getSimpleName(), request.userNickname);
            }

            searchSpecification.setUser(userOptional.get());
        }

        searchSpecification.setLimit(request.limit);
        searchSpecification.setOffset(request.offset);

        List<UserRating> foundUserRatings = userRatingOrchestrationService.search(context, searchSpecification);

        final SearchUserRatingsResult result = new SearchUserRatingsResult();
        result.total = userRatingOrchestrationService.total(context, searchSpecification);
        result.items = Lists.transform(
                foundUserRatings,
                new Function<UserRating, SearchUserRatingsResult.UserRating>() {
                    @Override
                    public SearchUserRatingsResult.UserRating apply(UserRating input) {
                        SearchUserRatingsResult.UserRating resultUserRating = new SearchUserRatingsResult.UserRating();
                        resultUserRating.active = input.getActive();
                        resultUserRating.code = input.getCode();
                        resultUserRating.comment = input.getComment();
                        resultUserRating.createTimestamp = input.getCreateTimestamp().getTime();
                        resultUserRating.modifyTimestamp = input.getModifyTimestamp().getTime();
                        resultUserRating.userRatingStabilityCode = null!=input.getUserRatingStability() ? input.getUserRatingStability().getCode() : null;
                        resultUserRating.naturalLanguageCode = input.getNaturalLanguage().getCode();
                        resultUserRating.pkgVersion = createPkgVersion(input.getPkgVersion());
                        resultUserRating.rating = input.getRating();
                        resultUserRating.user = createUser(input.getUser());
                        return resultUserRating;
                    }
                }
        );

        return result;
    }


}
