/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.nimbusds.jose.util.Pair;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.MappedSelect;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._HaikuDepot;
import org.haiku.haikudepotserver.support.StoppableConsumer;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinatesComparator;
import org.haiku.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

// Implementation note; the search query here is done using raw SQL as a Cayenne template. This is done in this
// way because the aggregation logic is complex enough to need to be done in SQL and the search query needs to
// be the same across both.

@Service
public class UserRatingServiceImpl implements UserRatingService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(UserRatingServiceImpl.class);

    /**
     * <p>When an aggregate query runs over the ratings then some {@link UserRating} may have no rating value and
     * in this case, im the returned aggregate, the rating used is this one.</p>
     */
    private final static int USER_RATING_NONE = -1;

    private final static String KEY_CODE = "code";
    private final static String KEY_TOTAL = "total";
    private final static String KEY_COUNT = "count";
    private final static String KEY_RATING = "rating";

    /**
     * <p>When the SQL query runs it can return a number of different response types. This enum controls the
     * different forms that the query can return.</p>
     */
    private enum QueryResultType {
        CODE,
        TOTAL,
        TOTALS_BY_RATING
    }

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
        MappedSelect<DataRow> mappedSelect = createQuery(search, QueryResultType.CODE).limit(100);

        // now loop through the user ratings.

        while(true) {
            mappedSelect = mappedSelect.offset(count);
            List<UserRating> userRatings = dataRowsWithCodeToUserRatings(context, mappedSelect.select(context));

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

    private MappedSelect<DataRow> createQuery(
            UserRatingSearchSpecification search,
            QueryResultType queryResultType) {
        return MappedSelect.query(_HaikuDepot.SEARCH_USER_RATINGS_QUERYNAME, DataRow.class).params(Map.of(
               "resultType", queryResultType,
                "search", search
        )).limit(search.getLimit()).offset(search.getOffset());
    }


    private List<UserRating> dataRowsWithCodeToUserRatings(ObjectContext context, List<DataRow> dataRows) {
        List<String> userRatingCodes = dataRows.stream().map(dr -> (String) dr.get(KEY_CODE)).toList();

        if (userRatingCodes.isEmpty()) {
            return List.of();
        }

        return ObjectSelect.query(UserRating.class).where(UserRating.CODE.in(userRatingCodes)).select(context);
    }

    @Override
    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        return dataRowsWithCodeToUserRatings(context, createQuery(search, QueryResultType.CODE).select(context));
    }

    @Override
    public Map<Short, Long> totalsByRating(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        return createQuery(search, QueryResultType.TOTALS_BY_RATING).select(context)
                .stream()
                .map(dr -> Pair.of(((Number) dr.get(KEY_RATING)).shortValue(), ((Number) dr.get(KEY_COUNT)).longValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
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
        Preconditions.checkNotNull(user);
        EJBQLQuery query = new EJBQLQuery("SELECT DISTINCT ur.pkgVersion.pkg.name FROM UserRating ur WHERE ur.user=?1");
        query.setParameter(1,user);
        return (List<String>) context.performQuery(query);
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
            return Collections.emptyList();
        }

        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append("SELECT DISTINCT ur.user.nickname FROM UserRating ur WHERE ur.user.active=true AND ur.active=true");
        queryBuilder.append(" AND (");

        for (int i = 0; i < pkgVersions.size(); i++) {
            if (0 != i) {
                queryBuilder.append(" OR ");
            }

            queryBuilder.append("ur.pkgVersion=?").append(i + 1);
        }

        queryBuilder.append(")");

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

        for(int i=0;i<pkgVersions.size();i++) {
            query.setParameter(i + 1, pkgVersions.get(i));
        }

        return context.performQuery(query);
    }

    /**
     * <p>This method will go through all of the relevant packages and will derive their user ratings.</p>
     */

    @Override
    public void updateUserRatingDerivationsForAllPkgs() {
        ObjectContext context = serverRuntime.newContext();

        String builder = "SELECT p.name FROM " +
                Pkg.class.getSimpleName() +
                " p" +
                " WHERE p.active=true" +
                " ORDER BY p.name DESC";

        List<String> pkgNames = context.performQuery(new EJBQLQuery(builder));

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

        String builder = "SELECT r.code FROM\n" +
                Repository.class.getSimpleName() +
                " r\n" +
                "WHERE EXISTS(SELECT pv FROM\n" +
                PkgVersion.class.getSimpleName() +
                " pv WHERE pv.pkg.name = :name\n" +
                " AND pv.repositorySource.repository = r)\n" +
                "ORDER BY r.code DESC";

        EJBQLQuery query = new EJBQLQuery(builder);
        query.setParameter("name", pkgName);
        List<String> repositoryCodes = (List<String>) context.performQuery(query);

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
        Optional<DerivedUserRating> rating = userRatingDerivation(context, pkg, repository);

        LOGGER.info("calculated the user rating for {} in {} in {}ms", pkg, repository, System.currentTimeMillis() - beforeMillis);

        if(rating.isEmpty()) {
            LOGGER.info("unable to establish a user rating for {} in {}", pkg, repository);
        }
        else {
            LOGGER.info(
                    "user rating established for {} in {}; {} (sample {})",
                    pkg,
                    repository,
                    rating.get().getRating(),
                    rating.get().getSampleSize());
        }

        Optional<PkgUserRatingAggregate> pkgUserRatingAggregateOptional = pkg.getPkgUserRatingAggregate(repository);

        if(rating.isPresent()) {

            PkgUserRatingAggregate pkgUserRatingAggregate = pkgUserRatingAggregateOptional.orElseGet(() -> {
                PkgUserRatingAggregate value = context.newObject(PkgUserRatingAggregate.class);
                value.setRepository(repository);
                value.setPkg(pkg);
                return value;
            });

            pkgUserRatingAggregate.setDerivedRating(rating.get().getRating());
            pkgUserRatingAggregate.setDerivedRatingSampleSize(rating.get().getSampleSize());
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

    Optional<DerivedUserRating> userRatingDerivation(ObjectContext context, Pkg pkg, Repository repository) {
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

            // now we need to find all of the package versions that are including this one or newer.

            {
                final VersionCoordinatesComparator mainPartsVersionCoordinatesComparator = new VersionCoordinatesComparator(true);
                pkgVersions = pkgVersions
                        .stream()
                        .filter(pv -> mainPartsVersionCoordinatesComparator.compare(pv.toVersionCoordinates(), oldestVersionCoordinates) >= 0)
                        .collect(Collectors.toList());
            }

            // only one user rating should taken from each user; the latest one.  Get a list of all the
            // people who have rated these versions.

            List<Short> ratings = new ArrayList<>();
            List<String> userNicknames = getUserNicknamesWhoHaveRatedPkgVersions(context, pkgVersions);

            for(String nickname : userNicknames) {
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

                if(!userRatingsForUser.isEmpty()) {
                    UserRating latestUserRatingForUser = userRatingsForUser.get(userRatingsForUser.size()-1);

                    if(null!=latestUserRatingForUser.getRating()) {
                        ratings.add(latestUserRatingForUser.getRating());
                    }
                }

            }

            // now generate an average from those ratings found.

            if(ratings.size() >= userRatingsDerivationMinRatings) {
                return Optional.of(new DerivedUserRating(
                    averageAsFloat(ratings),
                    ratings.size()));
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


    /**
     * <P>This is used as a model class / return value for the derivation of the user rating.</P>
     */

    static class DerivedUserRating {

        private final float rating;
        private final int sampleSize;

        DerivedUserRating(float rating, int sampleSize) {
            this.rating = rating;
            this.sampleSize = sampleSize;
        }

        public float getRating() {
            return rating;
        }

        int getSampleSize() {
            return sampleSize;
        }
    }

}
