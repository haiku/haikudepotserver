/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.*;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.EJBQLQuery;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
        List<Object> parameters = Lists.newArrayList();

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

        List<String> whereExpressions = Lists.newArrayList();

        whereExpressions.add("ur.user.active = true");

        if (!search.getIncludeInactive()) {
            whereExpressions.add("ur." + UserRating.ACTIVE_PROPERTY + " = true");
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

        return Joiner.on(" AND ").join(whereExpressions);

    }

    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<Object> parameters = Lists.newArrayList();
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

        List<Object> parameters = Lists.newArrayList();
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

    public void updateUserRatingDerivation(String pkgName) {
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));

        ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, pkgName);

        if(!pkgOptional.isPresent()) {
            throw new IllegalStateException("user derivation job submitted, but no pkg was found; " + pkgName);
        }

        long beforeMillis = System.currentTimeMillis();
        Optional<DerivedUserRating> rating = userRatingDerivation(context, pkgOptional.get());

        LOGGER.info(
                "calculated the user rating for {} in {}ms",
                pkgOptional.get(),
                System.currentTimeMillis() - beforeMillis);

        if(!rating.isPresent()) {
            LOGGER.info("unable to establish a user rating for {}", pkgOptional.get());
        }
        else {
            LOGGER.info(
                    "user rating established for {}; {} (sample {})",
                    pkgOptional.get(),
                    rating.get().getRating(),
                    rating.get().getSampleSize());
        }

        if(rating.isPresent()) {
            pkgOptional.get().setDerivedRating(rating.get().getRating());
            pkgOptional.get().setDerivedRatingSampleSize(rating.get().getSampleSize());
        }
        else {
            pkgOptional.get().setDerivedRating(null);
            pkgOptional.get().setDerivedRatingSampleSize(0);
        }

        context.commitChanges();

        LOGGER.info("did persist user rating for {}", pkgOptional.get());
    }

    /**
     * <p>This method will calculate the user rating for the package.  It may not be possible to generate a
     * user rating; in which case, an absent {@link com.google.common.base.Optional} is returned.</p>
     */

    public Optional<DerivedUserRating> userRatingDerivation(ObjectContext context, Pkg pkg) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);

        // haul all of the pkg versions into memory first.

        List<PkgVersion> pkgVersions = PkgVersion.getForPkg(context, pkg);

        if(!pkgVersions.isEmpty()) {

            final VersionCoordinatesComparator versionCoordinatesComparator = new VersionCoordinatesComparator();

            // convert the package versions into coordinates and sort those.

            List<VersionCoordinates> versionCoordinates = Lists.newArrayList(ImmutableSet.copyOf(Iterables.transform(
                    pkgVersions,
                    new Function<PkgVersion, VersionCoordinates>() {
                        @Override
                        public VersionCoordinates apply(PkgVersion input) {
                            return input.toVersionCoordinates().toVersionCoordinatesWithoutPreReleaseOrRevision();
                        }
                    }
            )));

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
                pkgVersions = Lists.newArrayList(Iterables.filter(pkgVersions, new Predicate<PkgVersion>() {
                    @Override
                    public boolean apply(PkgVersion input) {
                        return mainPartsVersionCoordinatesComparator.compare(
                                input.toVersionCoordinates(),
                                oldestVersionCoordinates) >= 0;
                    }
                }));
            }

            // only one user rating should taken from each user; the latest one.  Get a list of all of the
            // people who have rated these versions.

            List<Short> ratings = Lists.newArrayList();
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
                    UserRating latestUserRatingForUser = Iterables.getLast(userRatingsForUser);

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

        return Optional.absent();
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
