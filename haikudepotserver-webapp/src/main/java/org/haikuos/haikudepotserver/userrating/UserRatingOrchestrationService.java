/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.support.Callback;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.haikuos.haikudepotserver.support.VersionCoordinatesComparator;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>This service is able to provide support for non-trivial operations around user ratings.</p>
 */

@Service
public class UserRatingOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingOrchestrationService.class);

    @Value("${userrating.aggregation.pkg.versionsback:2}")
    private int userRatingDerivationVersionsBack;

    @Value("${userrating.aggregation.pkg.minratings:3}")
    private int userRatingsDerivationMinRatings;

    @Resource
    private ServerRuntime serverRuntime;

    // -------------------------------------
    // ITERATION

    /**
     * <p>This will be called for each user rating in the system with some constraints.</p>
     * @param c is the callback to invoke.
     * @return the quantity of user ratings processed.
     */

    // TODO; somehow prefetch the user?  prefetch tree?

    public int each(
            ObjectContext context,
            UserRatingSearchSpecification search,
            Callback<UserRating> c) {

        Preconditions.checkNotNull(c);
        Preconditions.checkNotNull(context);

        int count = 0;
        StringBuilder queryExpression = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        queryExpression.append("SELECT ur FROM UserRating ur");

        if(null!=search) {
            queryExpression.append(" WHERE ");
            queryExpression.append(prepareWhereClause(parameters, context, search));
        }

        queryExpression.append(" ORDER BY ur.createTimestamp DESC, ur.code DESC");

        EJBQLQuery query = new EJBQLQuery(queryExpression.toString());

        for(int i=0;i<parameters.size();i++) {
            query.setParameter(i + 1, parameters.get(i));
        }

        query.setFetchLimit(100);

        // now loop through the user ratings.

        while(true) {

            query.setFetchOffset(count);
            List<UserRating> userRatings = (List<UserRating>) context.performQuery(query);

            if(userRatings.isEmpty()) {
                return count;
            }

            for(UserRating userRating : userRatings) {
                if(!c.process(userRating)) {
                    return count;
                }
            }

            count += userRatings.size();
        }

    }

    // -------------------------------------
    // SEARCH

    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            UserRatingSearchSpecification search) {

        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        DateTime now = new DateTime();

        List<String> whereExpressions = new ArrayList<>();

        whereExpressions.add("ur.user.active = true");

        if (!search.getIncludeInactive()) {
            whereExpressions.add("ur." + UserRating.ACTIVE_PROPERTY + " = true");
        }

        if(null != search.getRepository()) {
            parameterAccumulator.add(search.getRepository());
            whereExpressions.add(
                    "ur."
                            + UserRating.PKG_VERSION_PROPERTY + "."
                            + PkgVersion.REPOSITORY_SOURCE_PROPERTY + "."
                            + RepositorySource.REPOSITORY_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if (null != search.getDaysSinceCreated()) {
            parameterAccumulator.add(new java.sql.Timestamp(now.minusDays(search.getDaysSinceCreated()).getMillis()));
            whereExpressions.add("ur." + UserRating.CREATE_TIMESTAMP_PROPERTY + " > ?" + parameterAccumulator.size());
        }

        if (null != search.getPkg() && null == search.getPkgVersion()) {
            parameterAccumulator.add(search.getPkg());
            whereExpressions.add("ur." + UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if (null != search.getArchitecture() && null == search.getPkgVersion()) {
            parameterAccumulator.add(search.getArchitecture());
            whereExpressions.add("ur." + UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.ARCHITECTURE_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if (null != search.getPkgVersion()) {
            parameterAccumulator.add(search.getPkgVersion());
            whereExpressions.add("ur." + UserRating.PKG_VERSION_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if (null != search.getUser()) {
            parameterAccumulator.add(search.getUser());
            whereExpressions.add("ur." + UserRating.USER_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        return String.join(" AND ", whereExpressions);

    }

    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<Object> parameters = new ArrayList<>();
        EJBQLQuery query = new EJBQLQuery("SELECT ur FROM " + UserRating.class.getSimpleName() + " AS ur WHERE " + prepareWhereClause(parameters, context, search) + " ORDER BY ur." + UserRating.CREATE_TIMESTAMP_PROPERTY + " DESC");
        query.setFetchOffset(search.getOffset());
        query.setFetchLimit(search.getLimit());

        for(int i=0;i<parameters.size();i++) {
            query.setParameter(i+1,parameters.get(i));
        }

        //noinspection unchecked
        return context.performQuery(query);
    }

    public long total(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<Object> parameters = new ArrayList<>();
        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(ur) FROM UserRating AS ur WHERE " + prepareWhereClause(parameters, context, search));

        for(int i=0;i<parameters.size();i++) {
            ejbQuery.setParameter(i+1,parameters.get(i));
        }

        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(ejbQuery);

        switch(result.size()) {
            case 1:
                return result.get(0).longValue();

            default:
                throw new IllegalStateException("expected 1 row from count query, but got "+result.size());
        }
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

        if(pkgVersions.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append("SELECT DISTINCT ur.user.nickname FROM UserRating ur WHERE ur.user.active=true AND ur.active=true");
        queryBuilder.append(" AND (");

        for(int i=0;i<pkgVersions.size();i++) {
            if(0!=i) {
                queryBuilder.append(" OR ");
            }

            queryBuilder.append("ur.pkgVersion=?"+Integer.toString(i + 1));
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

    public void updateUserRatingDerivationsForAllPkgs() {
        ObjectContext context = serverRuntime.getContext();

        StringBuilder builder = new StringBuilder();
        builder.append("SELECT p.name FROM ");
        builder.append(Pkg.class.getSimpleName());
        builder.append(" p");
        builder.append(" WHERE p.active=true");
        builder.append(" ORDER BY p.name DESC");

        List<String> pkgNames = context.performQuery(new EJBQLQuery(builder.toString()));

        LOGGER.info("will derive and store user ratings for {} packages", pkgNames.size());

        for (String pkgName : pkgNames) {
            updateUserRatingDerivation(pkgName);
        }

        LOGGER.info("did derive and store user ratings for {} packages", pkgNames.size());
    }

    /**
     * <p>This method will update the user rating aggregate across all appropriate
     * repositories.</p>
     */

    public void updateUserRatingDerivation(String pkgName) {
         Preconditions.checkArgument(!Strings.isNullOrEmpty(pkgName), "the name of the package is required");

        ObjectContext context = serverRuntime.getContext();

        StringBuilder builder = new StringBuilder();
        builder.append("SELECT r.code FROM\n");
        builder.append(Repository.class.getSimpleName());
        builder.append(" r\n");
        builder.append("WHERE EXISTS(SELECT pv FROM\n");
        builder.append(PkgVersion.class.getSimpleName());
        builder.append(" pv WHERE pv.pkg.name = :name\n");
        builder.append(" AND pv.repositorySource.repository = r)\n");
        builder.append("ORDER BY r.code DESC");

        EJBQLQuery query = new EJBQLQuery(builder.toString());
        query.setParameter("name", pkgName);
        List<String> repositoryCodes = (List<String>) context.performQuery(query);

        for(String repositoryCode : repositoryCodes) {
            updateUserRatingDerivation(pkgName, repositoryCode);
        }
    }

    /**
     * <p>This method will update the stored user rating aggregate (average) for the package for the
     * specified repository.</p>
     */

    public void updateUserRatingDerivation(String pkgName, String repositoryCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));
        Preconditions.checkState(!Strings.isNullOrEmpty(repositoryCode));

        ObjectContext context = serverRuntime.getContext();
        Pkg pkg = Pkg.getByName(context, pkgName)
                .orElseThrow(() -> new IllegalStateException("user derivation job submitted, but no pkg was found; " + pkgName));
        Repository repository = Repository.getByCode(context, repositoryCode)
                .orElseThrow(() -> new IllegalStateException("user derivation job submitted, but no repository was found; " + repositoryCode));

        long beforeMillis = System.currentTimeMillis();
        Optional<DerivedUserRating> rating = userRatingDerivation(context, pkg, repository);

        LOGGER.info("calculated the user rating for {} in {} in {}ms", pkg, repository, System.currentTimeMillis() - beforeMillis);

        if(!rating.isPresent()) {
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
        }
        else {

            // if there is no derived user rating then there should also be no database record
            // for the user rating.

            if(pkgUserRatingAggregateOptional.isPresent()) {
                pkg.removeFromPkgUserRatingAggregates(pkgUserRatingAggregateOptional.get());
                context.deleteObject(pkgUserRatingAggregateOptional.get());
            }
        }

        context.commitChanges();

        LOGGER.info("did update user rating for {} in {}", pkg, repository);
    }

    /**
     * <p>This method will calculate the user rating for the package.  It may not be possible to generate a
     * user rating; in which case, an absent {@link Optional} is returned.</p>
     */

    public Optional<DerivedUserRating> userRatingDerivation(ObjectContext context, Pkg pkg, Repository repository) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);

        // haul all of the pkg versions into memory first.

        List<PkgVersion> pkgVersions = PkgVersion.getForPkg(context, pkg, repository, false); // active only

        if(!pkgVersions.isEmpty()) {

            final VersionCoordinatesComparator versionCoordinatesComparator = new VersionCoordinatesComparator();

            // convert the package versions into coordinates and sort those.

            List<VersionCoordinates> versionCoordinates = pkgVersions
                    .stream()
                    .map(pv -> pv.toVersionCoordinates().toVersionCoordinatesWithoutPreReleaseOrRevision())
                    .distinct()
                    .collect(Collectors.toList());

            Collections.sort(versionCoordinates, versionCoordinatesComparator);

            // work back VERSIONS_BACK from the latest in order to find out where to start fishing out user
            // ratings from.

            final VersionCoordinates oldestVersionCoordinates;

            if (versionCoordinates.size() < userRatingDerivationVersionsBack + 1) {
                oldestVersionCoordinates = versionCoordinates.get(0);
            }
            else {
                oldestVersionCoordinates = versionCoordinates.get(versionCoordinates.size()-(userRatingDerivationVersionsBack +1));
            }

            // now we need to find all of the package versions that are including this one or newer.

            {
                final VersionCoordinatesComparator mainPartsVersionCoordinatesComparator = new VersionCoordinatesComparator(true);
                pkgVersions = pkgVersions
                        .stream()
                        .filter(pv -> mainPartsVersionCoordinatesComparator.compare(pv.toVersionCoordinates(), oldestVersionCoordinates) >= 0)
                        .collect(Collectors.toList());
            }

            // only one user rating should taken from each user; the latest one.  Get a list of all of the
            // people who have rated these versions.

            List<Short> ratings = new ArrayList<>();
            List<String> userNicknames = getUserNicknamesWhoHaveRatedPkgVersions(context, pkgVersions);

            for(String nickname : userNicknames) {
                User user = User.getByNickname(context, nickname).get();

                List<UserRating> userRatingsForUser = UserRating.getByUserAndPkgVersions(context, user, pkgVersions);

                Collections.sort(userRatingsForUser, new Comparator<UserRating>() {
                    @Override
                    public int compare(UserRating o1, UserRating o2) {
                        int c = versionCoordinatesComparator.compare(
                                o1.getPkgVersion().toVersionCoordinates(),
                                o2.getPkgVersion().toVersionCoordinates());

                        if(0==c) {
                            c = o1.getCreateTimestamp().compareTo(o2.getCreateTimestamp());
                        }

                        // possibly not the best thing to sort on, but should provide a total ordering.

                        if(0==c) {
                            c = o1.getPkgVersion().getArchitecture().getCode().compareTo(o2.getPkgVersion().getArchitecture().getCode());
                        }

                        return c;
                    }
                });

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

    /**
     * <P>This is used as a model class / return value for the derivation of the user rating.</P>
     */

    public static class DerivedUserRating {

        private float rating;
        private int sampleSize;

        public DerivedUserRating(float rating, int sampleSize) {
            this.rating = rating;
            this.sampleSize = sampleSize;
        }

        public float getRating() {
            return rating;
        }

        public int getSampleSize() {
            return sampleSize;
        }
    }

}
