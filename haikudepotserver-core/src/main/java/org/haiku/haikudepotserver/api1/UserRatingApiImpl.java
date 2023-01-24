/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.haiku.haikudepotserver.api1.model.userrating.AbstractGetUserRatingResult;
import org.haiku.haikudepotserver.api1.model.userrating.AbstractUserRatingResult;
import org.haiku.haikudepotserver.api1.model.userrating.CreateUserRatingRequest;
import org.haiku.haikudepotserver.api1.model.userrating.CreateUserRatingResult;
import org.haiku.haikudepotserver.api1.model.userrating.GetUserRatingByUserAndPkgVersionRequest;
import org.haiku.haikudepotserver.api1.model.userrating.GetUserRatingByUserAndPkgVersionResult;
import org.haiku.haikudepotserver.api1.model.userrating.SearchUserRatingsRequest;
import org.haiku.haikudepotserver.api1.model.userrating.SearchUserRatingsResult;
import org.haiku.haikudepotserver.api1.model.userrating.UpdateUserRatingRequest;
import org.haiku.haikudepotserver.api1.model.userrating.UpdateUserRatingResult;
import org.haiku.haikudepotserver.api2.UserRatingApiService;
import org.haiku.haikudepotserver.api2.model.CreateUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.PkgVersionType;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateUserRatingFilter;
import org.haiku.haikudepotserver.api2.model.UpdateUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.User;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Deprecated
@Component("userRatingApiImplV1")
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/userrating") // TODO; legacy path - remove
public class UserRatingApiImpl implements UserRatingApi {

    private final UserRatingApiService userRatingApiService;

    public UserRatingApiImpl(UserRatingApiService userRatingApiService) {
        this.userRatingApiService = Preconditions.checkNotNull(userRatingApiService);
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
        result.repositorySourceCode = pkgVersion.getRepositorySource().getCode();
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

    @Override
    public GetUserRatingByUserAndPkgVersionResult getUserRatingByUserAndPkgVersion(GetUserRatingByUserAndPkgVersionRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname), "the user nickname must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionArchitectureCode), "the pkg version architecture code must be supplied");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionMajor),"the package version major code must be supplied");

        org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionResult resultV2
                = userRatingApiService.getUserRatingByUserAndPkgVersion(new GetUserRatingByUserAndPkgVersionRequestEnvelope()
                .repositoryCode(request.repositoryCode)
                .repositorySourceCode(request.repositorySourceCode)
                .userNickname(request.userNickname)
                .pkgName(request.pkgName)
                .pkgVersionArchitectureCode(request.pkgVersionArchitectureCode)
                .pkgVersionMajor(request.pkgVersionMajor)
                .pkgVersionMinor(request.pkgVersionMinor)
                .pkgVersionMicro(request.pkgVersionMicro)
                .pkgVersionPreRelease(request.pkgVersionPreRelease)
                .pkgVersionRevision(request.pkgVersionRevision));

        GetUserRatingByUserAndPkgVersionResult result = new GetUserRatingByUserAndPkgVersionResult();
        result.active = resultV2.getActive();
        result.code = resultV2.getCode();
        result.naturalLanguageCode = resultV2.getNaturalLanguageCode();
        result.rating = Optional.ofNullable(resultV2.getRating()).map(Number::shortValue).orElse(null);
        result.comment = resultV2.getComment();
        result.modifyTimestamp = resultV2.getModifyTimestamp();
        result.createTimestamp = resultV2.getCreateTimestamp();
        result.userRatingStabilityCode = resultV2.getUserRatingStabilityCode();

        result.pkgVersion = new AbstractUserRatingResult.PkgVersion();
        result.pkgVersion.repositoryCode = resultV2.getPkgVersion().getRepositoryCode();
        result.pkgVersion.repositorySourceCode = resultV2.getPkgVersion().getRepositorySourceCode();
        result.pkgVersion.architectureCode = resultV2.getPkgVersion().getArchitectureCode();
        result.pkgVersion.major = resultV2.getPkgVersion().getMajor();
        result.pkgVersion.minor = resultV2.getPkgVersion().getMinor();
        result.pkgVersion.micro = resultV2.getPkgVersion().getMicro();
        result.pkgVersion.preRelease = resultV2.getPkgVersion().getPreRelease();
        result.pkgVersion.revision = resultV2.getPkgVersion().getRevision();

        result.pkgVersion.pkg = new AbstractUserRatingResult.Pkg();
        result.pkgVersion.pkg.name = resultV2.getPkgVersion().getPkg().getName();

        result.user = new AbstractUserRatingResult.User();
        result.user.nickname = resultV2.getUser().getNickname();

        return result;
    }

    @Override
    public CreateUserRatingResult createUserRating(CreateUserRatingRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgVersionArchitectureCode));

        org.haiku.haikudepotserver.api2.model.CreateUserRatingResult resultV2
                = userRatingApiService.createUserRating(new CreateUserRatingRequestEnvelope()
                .repositoryCode(request.repositoryCode)
                .repositorySourceCode(request.repositorySourceCode)
                .naturalLanguageCode(request.naturalLanguageCode)
                .userNickname(request.userNickname)
                .userRatingStabilityCode(request.userRatingStabilityCode)
                .comment(request.comment)
                .rating(Optional.ofNullable(request.rating).map(Number::intValue).orElse(null))
                .pkgName(request.pkgName)
                .pkgVersionArchitectureCode(request.pkgVersionArchitectureCode)
                .pkgVersionMajor(request.pkgVersionMajor)
                .pkgVersionMinor(request.pkgVersionMinor)
                .pkgVersionMicro(request.pkgVersionMicro)
                .pkgVersionPreRelease(request.pkgVersionPreRelease)
                .pkgVersionRevision(request.pkgVersionRevision)
                .pkgVersionType(PkgVersionType.fromValue(request.pkgVersionType.name())));

        return new CreateUserRatingResult(resultV2.getCode());
    }

    @Override
    public UpdateUserRatingResult updateUserRating(UpdateUserRatingRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.code));
        Preconditions.checkNotNull(request.filter);

        userRatingApiService.updateUserRating(new UpdateUserRatingRequestEnvelope()
                .code(request.code)
                .active(request.active)
                .naturalLanguageCode(request.naturalLanguageCode)
                .userRatingStabilityCode(request.userRatingStabilityCode)
                .comment(request.comment)
                .rating(Optional.ofNullable(request.rating).map(Number::intValue).orElse(null))
                .filter(CollectionUtils.emptyIfNull(request.filter).stream()
                        .map(f -> UpdateUserRatingFilter.fromValue(f.name()))
                        .toList()));

        return new UpdateUserRatingResult();
    }

    @Override
    public SearchUserRatingsResult searchUserRatings(SearchUserRatingsRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.limit);
        Preconditions.checkState(request.limit > 0);

        org.haiku.haikudepotserver.api2.model.SearchUserRatingsResult resultV2 = userRatingApiService.searchUserRatings(new SearchUserRatingsRequestEnvelope()
                .expression(request.expression)
                .expressionType(
                        Optional.ofNullable(request.expressionType)
                                .map(Enum::name)
                                .map(SearchUserRatingsRequestEnvelope.ExpressionTypeEnum::fromValue)
                                .orElse(null))
                .offset(request.offset)
                .limit(request.limit)
                .repositoryCode(request.repositoryCode)
                .repositorySourceCode(request.repositorySourceCode)
                .userNickname(request.userNickname)
                .pkgName(request.pkgName)
                .pkgVersionArchitectureCode(request.pkgVersionArchitectureCode)
                .pkgVersionMajor(request.pkgVersionMajor)
                .pkgVersionMinor(request.pkgVersionMinor)
                .pkgVersionMicro(request.pkgVersionMinor)
                .pkgVersionPreRelease(request.pkgVersionPreRelease)
                .pkgVersionRevision(request.pkgVersionRevision)
                .daysSinceCreated(Optional.ofNullable(request.daysSinceCreated).map(Number::intValue).orElse(null))
        );

        SearchUserRatingsResult result = new SearchUserRatingsResult();
        result.total = resultV2.getTotal();
        result.items = resultV2.getItems().stream()
                .map(itemV2 -> {
                    SearchUserRatingsResult.UserRating item = new SearchUserRatingsResult.UserRating();

                    item.code = itemV2.getCode();
                    item.naturalLanguageCode = itemV2.getNaturalLanguageCode();
                    item.userRatingStabilityCode = itemV2.getUserRatingStabilityCode();
                    item.active = itemV2.getActive();
                    item.comment = itemV2.getComment();
                    item.modifyTimestamp = itemV2.getModifyTimestamp();
                    item.createTimestamp = itemV2.getCreateTimestamp();
                    item.rating = Optional.ofNullable(itemV2.getRating())
                            .map(Number::shortValue)
                            .orElse(null);

                    item.pkgVersion = new AbstractUserRatingResult.PkgVersion();
                    item.pkgVersion.repositoryCode = itemV2.getPkgVersion().getRepositoryCode();
                    item.pkgVersion.repositorySourceCode = itemV2.getPkgVersion().getRepositorySourceCode();
                    item.pkgVersion.architectureCode = itemV2.getPkgVersion().getArchitectureCode();
                    item.pkgVersion.major = itemV2.getPkgVersion().getMajor();
                    item.pkgVersion.minor = itemV2.getPkgVersion().getMinor();
                    item.pkgVersion.micro = itemV2.getPkgVersion().getMicro();
                    item.pkgVersion.preRelease = itemV2.getPkgVersion().getPreRelease();
                    item.pkgVersion.revision = itemV2.getPkgVersion().getRevision();

                    item.pkgVersion.pkg = new AbstractUserRatingResult.Pkg();
                    item.pkgVersion.pkg.name = itemV2.getPkgVersion().getPkg().getName();

                    item.user = new AbstractUserRatingResult.User();
                    item.user.nickname = itemV2.getUser().getNickname();

                    return item;
                })
                .toList();
        return result;
    }

}
