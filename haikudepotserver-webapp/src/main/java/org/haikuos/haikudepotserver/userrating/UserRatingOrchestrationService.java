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

    protected static Logger logger = LoggerFactory.getLogger(UserRatingOrchestrationService.class);

    @Value("${userrating.aggregation.pkg.versionsback:2}")
    int userRatingDerivationVersionsBack;

    @Value("${userrating.aggregation.pkg.minratings:3}")
    int userRatingsDerivationMinRatings;

    @Resource
    ServerRuntime serverRuntime;

    // ------------------------------
    // SEARCH

    // [apl 11.may.2014]
    // SelectQuery has no means of getting a count.  This is a bit of an annoying limitation, but can be worked around
    // by using EJBQL.  However converting from an Expression to EJBQL has a problem (see CAY-1932) so for the time
    // being, just use EJBQL directly by assembling strings and convert back later.

    // NOTE; raw EJBQL can be replaced with Expressions once CAY-1932 is fixed.
    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            UserRatingSearchSpecification search) {

        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        DateTime now = new DateTime();

        List<String> whereExpressions = Lists.newArrayList();

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

//    public SelectQuery prepare(ObjectContext context, UserRatingSearchSpecification search) {
//        Preconditions.checkNotNull(search);
//        Preconditions.checkNotNull(context);
//        DateTime now = new DateTime();
//
//        // build up a list of expressions
//
//        List<Expression> expressions = Lists.newArrayList();
//
//        if (!search.getIncludeInactive()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.ACTIVE_PROPERTY,
//                    Boolean.TRUE));
//        }
//
//        if (null != search.getDaysSinceCreated()) {
//            expressions.add(ExpressionFactory.greaterExp(
//                    UserRating.CREATE_TIMESTAMP_PROPERTY,
//                    new java.sql.Timestamp(now.minusDays(search.getDaysSinceCreated()).getMillis())));
//        }
//
//        if (null != search.getPkg() && null == search.getPkgVersion()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY,
//                    search.getPkg()));
//        }
//
//        if (null != search.getArchitecture() && null == search.getPkgVersion()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.ARCHITECTURE_PROPERTY,
//                    search.getArchitecture()));
//        }
//
//        if (null != search.getPkgVersion()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.PKG_VERSION_PROPERTY,
//                    search.getPkgVersion()));
//        }
//
//        if (null != search.getUser()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.USER_PROPERTY,
//                    search.getUser()));
//        }
//
//        return new SelectQuery(UserRating.class, ExpressionHelper.andAll(expressions));
//    }
//
//    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
//        Preconditions.checkNotNull(search);
//        Preconditions.checkNotNull(context);
//
//        SelectQuery selectQuery = prepare(context, search);
//        selectQuery.setFetchOffset(search.getOffset());
//        selectQuery.setFetchLimit(search.getLimit());
//        selectQuery.addOrdering(new Ordering(UserRating.CREATE_TIMESTAMP_PROPERTY, SortOrder.DESCENDING));
//
//        //noinspection unchecked
//        return context.performQuery(selectQuery);
//    }
//
//    public long total(ObjectContext context, UserRatingSearchSpecification search) {
//        Preconditions.checkNotNull(search);
//        Preconditions.checkNotNull(context);
//
//        SelectQuery selectQuery = prepare(context, search);
//        List<Object> parameters = Lists.newArrayList();
//        String ejbql = selectQuery.getQualifier().toEJBQL(parameters, "ur");
//
//        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(ur) FROM UserRating AS ur WHERE " + ejbql);
//
//        for(int i=0;i<parameters.size();i++) {
//            ejbQuery.setParameter(i+1,parameters.get(i));
//        }
//
//        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(ejbQuery);
//
//        switch(result.size()) {
//            case 1:
//                return result.get(0).longValue();
//
//            default:
//                throw new IllegalStateException("expected 1 row from count query, but got "+result.size());
//        }
//    }

    // ------------------------------
    // DERIVATION ALGORITHM

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

    public void updateUserRatingDerivation(String pkgName) {
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));

        ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, pkgName);

        if(!pkgOptional.isPresent()) {
            throw new IllegalStateException("user derivation job submitted, but no pkg was found; " + pkgName);
        }

        long beforeMillis = System.currentTimeMillis();
        Optional<DerivedUserRating> rating = userRatingDerivation(context, pkgOptional.get());

        logger.info(
                "calculated the user rating for {} in {}ms",
                pkgOptional.get(),
                System.currentTimeMillis() - beforeMillis);

        if(!rating.isPresent()) {
            logger.info("unable to establish a user rating for {}", pkgOptional.get());
        }
        else {
            logger.info("user rating established for {}; {}", pkgOptional.get(), rating.get());
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

        logger.info("did persist user rating for {}", pkgOptional.get());
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
