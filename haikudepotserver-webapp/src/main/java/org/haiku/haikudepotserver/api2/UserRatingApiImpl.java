/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.haiku.haikudepotserver.api1.model.PkgVersionType;
import org.haiku.haikudepotserver.api1.model.userrating.*;
import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;
import org.haiku.haikudepotserver.api2.model.CreateUserRatingResult;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionResult;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsResult;
import org.haiku.haikudepotserver.api2.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class UserRatingApiImpl extends AbstractApiImpl implements UserRatingApi {

    private org.haiku.haikudepotserver.api1.UserRatingApi userRatingApiV1;

    public UserRatingApiImpl(org.haiku.haikudepotserver.api1.UserRatingApi userRatingApiV1) {
        this.userRatingApiV1 = userRatingApiV1;
    }

    @Override
    public ResponseEntity<UpdateUserRatingResponseEnvelope> updateUserRating(@Valid UpdateUserRatingRequestEnvelope updateUserRatingRequestEnvelope) {
        UpdateUserRatingRequest requestV1 = new UpdateUserRatingRequest();
        requestV1.active = updateUserRatingRequestEnvelope.getActive();
        requestV1.code = updateUserRatingRequestEnvelope.getCode();
        requestV1.naturalLanguageCode = updateUserRatingRequestEnvelope.getNaturalLanguageCode();
        requestV1.userRatingStabilityCode = updateUserRatingRequestEnvelope.getUserRatingStabilityCode();
        requestV1.comment = updateUserRatingRequestEnvelope.getComment();
        requestV1.rating = Optional.ofNullable(updateUserRatingRequestEnvelope.getRating())
                .map(Integer::shortValue).orElse(null);
        requestV1.filter = CollectionUtils.emptyIfNull(updateUserRatingRequestEnvelope.getFilter())
                .stream().map(f -> UpdateUserRatingRequest.Filter.valueOf(f.name()))
                .collect(Collectors.toList());
        userRatingApiV1.updateUserRating(requestV1);
        return ResponseEntity.ok(new UpdateUserRatingResponseEnvelope().result(new UpdateUserRatingResult()));
    }

    @Override
    public ResponseEntity<CreateUserRatingResponseEnvelope> createUserRating(@Valid CreateUserRatingRequestEnvelope createUserRatingRequestEnvelope) {
        CreateUserRatingRequest requestV1 = new CreateUserRatingRequest();
        requestV1.repositoryCode = createUserRatingRequestEnvelope.getRepositoryCode();
        requestV1.naturalLanguageCode = createUserRatingRequestEnvelope.getNaturalLanguageCode();
        requestV1.userNickname = createUserRatingRequestEnvelope.getUserNickname();
        requestV1.userRatingStabilityCode = createUserRatingRequestEnvelope.getUserRatingStabilityCode();
        requestV1.comment = createUserRatingRequestEnvelope.getComment();
        requestV1.rating = Optional.ofNullable(createUserRatingRequestEnvelope.getRating()).map(Number::shortValue).orElse(null);
        requestV1.pkgName = createUserRatingRequestEnvelope.getPkgName();
        requestV1.pkgVersionArchitectureCode = createUserRatingRequestEnvelope.getPkgVersionArchitectureCode();
        requestV1.pkgVersionMajor = createUserRatingRequestEnvelope.getPkgVersionMajor();
        requestV1.pkgVersionMinor = createUserRatingRequestEnvelope.getPkgVersionMinor();
        requestV1.pkgVersionMicro = createUserRatingRequestEnvelope.getPkgVersionMicro();
        requestV1.pkgVersionPreRelease = createUserRatingRequestEnvelope.getPkgVersionPreRelease();
        requestV1.pkgVersionRevision = createUserRatingRequestEnvelope.getPkgVersionRevision();
        requestV1.pkgVersionType = Optional.ofNullable(createUserRatingRequestEnvelope.getPkgVersionType())
                .map(pvt -> PkgVersionType.valueOf(pvt.name())).orElse(null);
        org.haiku.haikudepotserver.api1.model.userrating.CreateUserRatingResult resultV1 = userRatingApiV1.createUserRating(requestV1);
        return ResponseEntity.ok(new CreateUserRatingResponseEnvelope().result(new CreateUserRatingResult().code(resultV1.code)));
    }

    @Override
    public ResponseEntity<GetUserRatingByUserAndPkgVersionResponseEnvelope> getUserRatingByUserAndPkgVersion(@Valid GetUserRatingByUserAndPkgVersionRequestEnvelope getUserRatingByUserAndPkgVersionRequestEnvelope) {
        GetUserRatingByUserAndPkgVersionRequest requestV1 = new GetUserRatingByUserAndPkgVersionRequest();
        requestV1.repositoryCode = getUserRatingByUserAndPkgVersionRequestEnvelope.getRepositoryCode();
        requestV1.userNickname = getUserRatingByUserAndPkgVersionRequestEnvelope.getUserNickname();
        requestV1.pkgName = getUserRatingByUserAndPkgVersionRequestEnvelope.getPkgName();
        requestV1.pkgVersionArchitectureCode = getUserRatingByUserAndPkgVersionRequestEnvelope.getPkgVersionArchitectureCode();
        requestV1.pkgVersionMajor = getUserRatingByUserAndPkgVersionRequestEnvelope.getPkgVersionMajor();
        requestV1.pkgVersionMinor = getUserRatingByUserAndPkgVersionRequestEnvelope.getPkgVersionMinor();
        requestV1.pkgVersionMicro = getUserRatingByUserAndPkgVersionRequestEnvelope.getPkgVersionMicro();
        requestV1.pkgVersionPreRelease = getUserRatingByUserAndPkgVersionRequestEnvelope.getPkgVersionPreRelease();
        requestV1.pkgVersionRevision = getUserRatingByUserAndPkgVersionRequestEnvelope.getPkgVersionRevision();
        org.haiku.haikudepotserver.api1.model.userrating.GetUserRatingByUserAndPkgVersionResult resultV1 = userRatingApiV1.getUserRatingByUserAndPkgVersion(requestV1);
        return ResponseEntity.ok(new GetUserRatingByUserAndPkgVersionResponseEnvelope()
                .result(new GetUserRatingByUserAndPkgVersionResult()
                        .code(resultV1.code)
                        .naturalLanguageCode(resultV1.naturalLanguageCode)
                        .user(new User().nickname(resultV1.user.nickname))
                        .userRatingStabilityCode(resultV1.userRatingStabilityCode)
                        .active(resultV1.active)
                        .comment(resultV1.comment)
                        .modifyTimestamp(resultV1.modifyTimestamp)
                        .createTimestamp(resultV1.createTimestamp)
                        .rating(Optional.ofNullable(resultV1.rating).map(Number::intValue).orElse(null))
                        .pkgVersion(mapV1ToV2(resultV1.pkgVersion)))
        );
    }

    @Override
    public ResponseEntity<SearchUserRatingsResponseEnvelope> searchUserRatings(@Valid SearchUserRatingsRequestEnvelope searchUserRatingsRequestEnvelope) {
        SearchUserRatingsRequest requestV1 = new SearchUserRatingsRequest();
        requestV1.repositoryCode = searchUserRatingsRequestEnvelope.getRepositoryCode();
        requestV1.pkgName = searchUserRatingsRequestEnvelope.getPkgName();
        requestV1.userNickname = searchUserRatingsRequestEnvelope.getUserNickname();
        requestV1.pkgVersionArchitectureCode = searchUserRatingsRequestEnvelope.getPkgVersionArchitectureCode();
        requestV1.pkgVersionMajor = searchUserRatingsRequestEnvelope.getPkgVersionMajor();
        requestV1.pkgVersionMinor = searchUserRatingsRequestEnvelope.getPkgVersionMinor();
        requestV1.pkgVersionMicro = searchUserRatingsRequestEnvelope.getPkgVersionMicro();
        requestV1.pkgVersionPreRelease = searchUserRatingsRequestEnvelope.getPkgVersionPreRelease();
        requestV1.pkgVersionRevision = searchUserRatingsRequestEnvelope.getPkgVersionRevision();
        requestV1.daysSinceCreated = searchUserRatingsRequestEnvelope.getDaysSinceCreated();
        requestV1.limit = searchUserRatingsRequestEnvelope.getLimit();
        requestV1.expression = searchUserRatingsRequestEnvelope.getExpression();
        requestV1.offset = searchUserRatingsRequestEnvelope.getOffset();
        requestV1.expressionType = Optional.ofNullable(searchUserRatingsRequestEnvelope.getExpressionType())
                .map(et -> AbstractSearchRequest.ExpressionType.valueOf(et.name()))
                .orElse(null);
        org.haiku.haikudepotserver.api1.model.userrating.SearchUserRatingsResult resultV1 = userRatingApiV1.searchUserRatings(requestV1);
        return ResponseEntity.ok(new SearchUserRatingsResponseEnvelope().result(mapV1ToV2(resultV1)));
    }

    private SearchUserRatingsResult mapV1ToV2(org.haiku.haikudepotserver.api1.model.userrating.SearchUserRatingsResult resultV1) {
        return new SearchUserRatingsResult()
                .total(resultV1.total)
                .items(ListUtils.emptyIfNull(resultV1.items).stream()
                        .map(item -> new SearchUserRatingsResultItems()
                                .code(item.code)
                                .naturalLanguageCode(item.naturalLanguageCode)
                                .userRatingStabilityCode(item.userRatingStabilityCode)
                                .active(item.active)
                                .comment(item.comment)
                                .modifyTimestamp(item.modifyTimestamp)
                                .createTimestamp(item.createTimestamp)
                                .rating(Optional.ofNullable(item.rating).map(Number::intValue).orElse(null))
                                .pkgVersion(mapV1ToV2(item.pkgVersion))
                                .user(new User().nickname(item.user.nickname)))
                        .collect(Collectors.toList()));
    }

    private PkgVersion mapV1ToV2(AbstractUserRatingResult.PkgVersion pkgVersion) {
        return new PkgVersion()
                .pkg(new PkgVersionPkg().name(pkgVersion.pkg.name))
                .repositoryCode(pkgVersion.repositoryCode)
                .architectureCode(pkgVersion.architectureCode)
                .major(pkgVersion.major)
                .minor(pkgVersion.minor)
                .micro(pkgVersion.micro)
                .preRelease(pkgVersion.preRelease)
                .revision(pkgVersion.revision);
    }

}
