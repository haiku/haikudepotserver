/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.support.StoppableConsumer;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinatesComparator;
import org.haiku.haikudepotserver.userrating.model.DerivedUserRating;
import org.haiku.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Implementation note; the search query here is done using raw SQL as a Cayenne template. This is done in this
// way because the aggregation logic is complex enough to need to be done in SQL and the search query needs to
// be the same across both.

@Service
public class UserRatingServiceImpl implements UserRatingService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(UserRatingServiceImpl.class);

    private final ServerRuntime serverRuntime;
    private final int userRatingDerivationVersionsBack;
    private final int userRatingsDerivationMinRatings;


    public UserRatingServiceImpl(
            ServerRuntime serverRuntime,
            @Value("${hds.user-rating.aggregation.pkg.versions-back:2}") int userRatingDerivationVersionsBack,
            @Value("${hds.user-rating.aggregation.pkg.min-ratings:3}") int userRatingsDerivationMinRatings) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.userRatingDerivationVersionsBack = userRatingDerivationVersionsBack;
        this.userRatingsDerivationMinRatings = userRatingsDerivationMinRatings;
    }

    // -------------------------------------
    // ITERATION

    /**
     * <p>This will be called for each user rating in the system with some constraints.</p>
     * @param c is the callback to invoke.
     * @return the quantity of user ratings processed.
     */

    // TODO; somehow prefetch the user?  prefetch tree?

    @Override
    public int each(
            ObjectContext context,
            UserRatingSearchSpecification search,
            StoppableConsumer<UserRating> c) {

        Preconditions.checkNotNull(c);
        Preconditions.checkNotNull(context);

        int count = 0;
        ObjectSelect<UserRating> select = createQuery(search).limit(100);

        // now loop through the user ratings.

        while(true) {
            select = select.offset(count);
            List<UserRating> userRatings = select.select(context);

            if (userRatings.isEmpty()) {
                return count;
            }

            for(UserRating userRating : userRatings) {
                if(!c.accept(userRating)) {
                    return count;
                }
            }

            count += userRatings.size();
        }
    }

    // -------------------------------------
    // SEARCH

    private ObjectSelect<UserRating> createQuery(UserRatingSearchSpecification search) {
        ObjectSelect<UserRating> query = ObjectSelect.query(UserRating.class)
                .where(UserRating.USER.dot(User.ACTIVE).isTrue())
                .and(UserRating.PKG_VERSION.dot(PkgVersion.ACTIVE).isTrue());

        if (!search.getIncludeInactive()) {
            query = query.and(UserRating.ACTIVE.isTrue());
        }

        if (null != search.getRepository()) {
            query = query.and(
                    UserRating.PKG_VERSION.dot(PkgVersion.REPOSITORY_SOURCE).dot(RepositorySource.REPOSITORY)
                            .eq(search.getRepository()));
        }

        if (null != search.getRepositorySource()) {
            query = query.and(
                    UserRating.PKG_VERSION.dot(PkgVersion.REPOSITORY_SOURCE)
                            .eq(search.getRepositorySource()));
        }

        if (null != search.getDaysSinceCreated()) {
            java.sql.Timestamp nowDaysAgoTimestamp = new java.sql.Timestamp(
                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(search.getDaysSinceCreated()));
            query = query.and(UserRating.CREATE_TIMESTAMP.gt(nowDaysAgoTimestamp));
        }

        if (null != search.getPkg() && null == search.getPkgVersion()) {
            query = query.and(UserRating.PKG_VERSION.dot(PkgVersion.PKG).eq(search.getPkg()));
        }

        if (null != search.getArchitecture() && null == search.getPkgVersion()) {
            query = query.and(UserRating.PKG_VERSION.dot(PkgVersion.ARCHITECTURE).eq(search.getArchitecture()));
        }

        if (null != search.getPkgVersion()) {
            query = query.and(UserRating.PKG_VERSION.eq(search.getPkgVersion()));
        }

        if (null != search.getPkgVersion()) {
            query = query.and(UserRating.USER.eq(search.getUser()));
        }

        return query;
    }

    @Override
    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        return createQuery(search)
                .limit(search.getLimit())
                .offset(search.getOffset())
                .select(context);
    }

    public long total(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        return createQuery(search).selectCount(context);
    }

    // ------------------------------
    // DERIVATION ALGORITHM

    /**
     * <p>If a user has their active / inactive state swapped, it is possible that it may have some bearing
     * on user ratings because user rating values from inactive users are not included.  The impact may be
     * small, but may also be significant in cases where the package has few ratings anyway.  This method
     * will return a list of package names for those packages that may be accordingly effected by a change
     * in a user's active flag.</p>
     */

    @Override
    public List<String> pkgNamesEffectedByUserActiveStateChange(ObjectContext context, User user) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(user);
        return ObjectSelect.query(UserRating.class)
                .where(UserRating.USER.eq(user))
                .column(UserRating.PKG_VERSION.dot(Pkg.NAME))
                .distinct()
                .select(context);
    }

    private float averageAsFloat(Collection<Short> ratings) {
        Preconditions.checkNotNull(ratings);

        if(ratings.isEmpty()) {
            return 0f;
        }

        int sum = 0;

        for(short rating : ratings) {
            sum += rating;
        }

        sum *= 100;
        sum /= ratings.size();

        return ((float) sum) / 100f;
    }

    private List<String> getUserNicknamesWhoHaveRatedPkgVersions(ObjectContext context, List<PkgVersion> pkgVersions) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkgVersions);

        if (pkgVersions.isEmpty()) {
            return List.of();
        }

        return ObjectSelect.query(UserRating.class)
                .where(UserRating.USER.dot(User.ACTIVE).isTrue())
                .and(UserRating.ACTIVE.isTrue())
                .and(ExpressionFactory.or(
                        pkgVersions.stream()
                                .map(UserRating.PKG_VERSION::eq)
                                .toList()))
                .column(UserRating.USER.dot(User.NICKNAME))
                .distinct()
                .select(context);
    }

    /**
     * <p>This method will go through all of the relevant packages and will derive their user ratings.</p>
     */

    @Override
    public void updateUserRatingDerivationsForAllPkgs() {
        ObjectContext context = serverRuntime.newContext();
        List<String> pkgNames = ObjectSelect.query(Pkg.class)
                .where(Pkg.ACTIVE.isTrue())
                .orderBy(Pkg.NAME.desc())
                .column(Pkg.NAME)
                .select(context);

        LOGGER.info("will derive and store user ratings for {} packages", pkgNames.size());

        for (String pkgName : pkgNames) {
            updateUserRatingDerivationsForPkg(pkgName);
        }

        LOGGER.info("did derive and store user ratings for {} packages", pkgNames.size());
    }

    /**
     * <p>This method will update the user rating aggregate across all appropriate
     * repositories.</p>
     */

    @Override
    public void updateUserRatingDerivationsForPkg(String pkgName) {
         Preconditions.checkArgument(!Strings.isNullOrEmpty(pkgName), "the name of the package is required");

        ObjectContext context = serverRuntime.newContext();

        List<String> repositoryCodes = ObjectSelect.query(PkgVersion.class)
                .where(PkgVersion.PKG.dot(Pkg.NAME).eq(pkgName))
                .column(PkgVersion.REPOSITORY_SOURCE.dot(RepositorySource.REPOSITORY).dot(Repository.CODE))
                .orderBy(PkgVersion.REPOSITORY_SOURCE.dot(RepositorySource.REPOSITORY).dot(Repository.CODE).desc())
                .distinct()
                .select(context);

        for(String repositoryCode : repositoryCodes) {
            updateUserRatingDerivation(pkgName, repositoryCode);
        }
    }

    @Override
    public void updateUserRatingDerivationsForUser(String userNickname) {
        Preconditions.checkArgument(StringUtils.isNotBlank(userNickname), "the nickname must be provided");
        ObjectContext context = serverRuntime.newContext();
        User user = User.getByNickname(context, userNickname);
        List<String> pkgNames = pkgNamesEffectedByUserActiveStateChange(context, user);
        LOGGER.info("will update user rating derivations for user [{}] including {} pkgs",
                user, pkgNames.size());
        pkgNames.forEach(this::updateUserRatingDerivationsForPkg);
    }

    /**
     * <p>This method will update the stored user rating aggregate (average) for the package for the
     * specified repository.</p>
     */

    private void updateUserRatingDerivation(String pkgName, String repositoryCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));
        Preconditions.checkState(!Strings.isNullOrEmpty(repositoryCode));

        ObjectContext context = serverRuntime.newContext();
        Pkg pkg = Pkg.tryGetByName(context, pkgName)
                .orElseThrow(() -> new IllegalStateException("user derivation job submitted, but no pkg was found; " + pkgName));
        Repository repository = Repository.tryGetByCode(context, repositoryCode)
                .orElseThrow(() -> new IllegalStateException("user derivation job submitted, but no repository was found; " + repositoryCode));

        long beforeMillis = System.currentTimeMillis();
        Optional<DerivedUserRating> rating = tryCreateUserRatingDerivation(context, pkg, repository);

        LOGGER.info("calculated the user rating for {} in {} in {}ms", pkg, repository, System.currentTimeMillis() - beforeMillis);

        if(rating.isEmpty()) {
            LOGGER.info("unable to establish a user rating for {} in {}", pkg, repository);
        }
        else {
            LOGGER.info(
                    "user rating established for {} in {}; {} (sample {})",
                    pkg,
                    repository,
                    rating.get().rating(),
                    rating.get().sampleSize());
        }

        Optional<PkgUserRatingAggregate> pkgUserRatingAggregateOptional = pkg.getPkgUserRatingAggregate(repository);

        if(rating.isPresent()) {

            PkgUserRatingAggregate pkgUserRatingAggregate = pkgUserRatingAggregateOptional.orElseGet(() -> {
                PkgUserRatingAggregate value = context.newObject(PkgUserRatingAggregate.class);
                value.setRepository(repository);
                value.setPkg(pkg);
                return value;
            });

            pkgUserRatingAggregate.setDerivedRating(rating.get().rating());
            pkgUserRatingAggregate.setDerivedRatingSampleSize((int) rating.get().sampleSize());
            pkg.setModifyTimestamp();
        }
        else {

            // if there is no derived user rating then there should also be no database record
            // for the user rating.

            if(pkgUserRatingAggregateOptional.isPresent()) {
                pkg.removeFromPkgUserRatingAggregates(pkgUserRatingAggregateOptional.get());
                context.deleteObject(pkgUserRatingAggregateOptional.get());
                pkg.setModifyTimestamp();
            }
        }

        context.commitChanges();

        LOGGER.info("did update user rating for {} in {}", pkg, repository);
    }

    /**
     * <p>This method will calculate the user rating for the package.  It may not be possible to generate a
     * user rating; in which case, an absent {@link Optional} is returned.</p>
     */

    @Override
    public Optional<DerivedUserRating> tryCreateUserRatingDerivation(
            ObjectContext context,
            Pkg pkg,
            Repository repository) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);

        // haul all the pkg versions into memory first.

        List<PkgVersion> pkgVersions = repository.getRepositorySources().stream()
                .flatMap(rs -> PkgVersion.findForPkg(context, pkg, rs, false).stream()) // active only
                .collect(Collectors.toList());

        if(!pkgVersions.isEmpty()) {

            final VersionCoordinatesComparator versionCoordinatesComparator = new VersionCoordinatesComparator();

            // convert the package versions into coordinates and sort those.

            List<VersionCoordinates> versionCoordinates = pkgVersions
                    .stream()
                    .map(pv -> pv.toVersionCoordinates().toVersionCoordinatesWithoutPreReleaseOrRevision())
                    .distinct()
                    .sorted(versionCoordinatesComparator)
                    .toList();

            // work back VERSIONS_BACK from the latest in order to find out where to start fishing out user
            // ratings from.

            final VersionCoordinates oldestVersionCoordinates;

            if (versionCoordinates.size() < userRatingDerivationVersionsBack + 1) {
                oldestVersionCoordinates = versionCoordinates.getFirst();
            }
            else {
                oldestVersionCoordinates = versionCoordinates.get(versionCoordinates.size() - (userRatingDerivationVersionsBack +1));
            }

            // now we need to find all the package versions that are including this one or newer.

            {
                final VersionCoordinatesComparator mainPartsVersionCoordinatesComparator = new VersionCoordinatesComparator(true);
                pkgVersions = pkgVersions
                        .stream()
                        .filter(pv -> mainPartsVersionCoordinatesComparator.compare(pv.toVersionCoordinates(), oldestVersionCoordinates) >= 0)
                            // ^ this comparator will ignore the pre-release and revision.
                        .collect(Collectors.toList());
            }

            // only one user rating should taken from each user; the latest one.  Get a list of all the
            // people who have rated these versions.

            List<Short> ratings = new ArrayList<>();
            List<String> userNicknames = getUserNicknamesWhoHaveRatedPkgVersions(context, pkgVersions);
            Map<Short, Long> ratingDistribution = new HashMap<>();

            // write zero values which can be incremented in the algorithm.
            IntStream.range(0, 6).forEach(i -> ratingDistribution.put((short) i, 0L));

            for (String nickname : userNicknames) {
                User user = User.getByNickname(context, nickname);

                List<UserRating> userRatingsForUser = new ArrayList<>(UserRating.getByUserAndPkgVersions(
                        context, user, pkgVersions));

                userRatingsForUser.sort((o1, o2) ->
                        ComparisonChain.start()
                                .compare(
                                        o1.getPkgVersion().toVersionCoordinates(),
                                        o2.getPkgVersion().toVersionCoordinates(),
                                        versionCoordinatesComparator)
                                .compare(o1.getCreateTimestamp(), o2.getCreateTimestamp())
                                .compare(
                                        o1.getPkgVersion().getArchitecture().getCode(),
                                        o2.getPkgVersion().getArchitecture().getCode())
                                .result());

                if (!userRatingsForUser.isEmpty()) {
                    UserRating latestUserRatingForUser = userRatingsForUser.getLast();

                    if (null != latestUserRatingForUser.getRating()) {
                        Short rating = latestUserRatingForUser.getRating();
                        ratings.add(rating);
                        ratingDistribution.put(rating, ratingDistribution.get(rating) + 1);
                    }
                }

            }

            // now generate an average from those ratings found.

            if (ratings.size() >= userRatingsDerivationMinRatings) {

                ratings.forEach(rating -> ratingDistribution.put(
                        rating,
                        ratingDistribution.getOrDefault(rating, 0L)));

                return Optional.of(new DerivedUserRating(
                        averageAsFloat(ratings),
                        ratings.size(),
                        ratingDistribution));
            }
        }

        return Optional.empty();
    }

    @Override
    public void removeUserRatingAtomically(String userRatingCode) {
        ObjectContext context = serverRuntime.newContext();
        UserRating userRating = UserRating.getByCode(context, userRatingCode);

        context.deleteObject(userRating);
        context.commitChanges();

        LOGGER.info("did delete user rating [{}]", userRatingCode);
    }

}
