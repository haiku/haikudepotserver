/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.haikuos.haikudepotserver.support.VersionCoordinatesComparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * <p>User ratings are collected from users.  User ratings can then be used to derive a overall or 'aggregated'
 * rating for a package.  This class is about deriving those composite ratings.  Because they make take a little
 * while to work out, they are not done in real time.  Instead they are handed asynchronously in the background.
 * This service also offers the function of making these derivations in the background.</p>
 */

@Service
public class UserRatingDerivationService {

    @Value("${userrating.aggregation.pkg.versionsback:2}")
    int versionsBack;

    @Value("${userrating.aggregation.pkg.minratings:3}")
    int minRatings ;

    @Resource
    PkgOrchestrationService pkgOrchestrationService;

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
     * <p>This method will calculate the user rating for the package.  It may not be possible to generate a
     * user rating; in which case, an absent {@link com.google.common.base.Optional} is returned.</p>
     */

    public Optional<Float> userRatingDerivation(ObjectContext context, Pkg pkg) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);

        Float result = null;

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

            if (versionCoordinates.size() < versionsBack + 1) {
                oldestVersionCoordinates = versionCoordinates.get(0);
            }
            else {
                oldestVersionCoordinates = versionCoordinates.get(versionCoordinates.size()-(versionsBack+1));
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

            if(ratings.size() >= minRatings) {
                result = averageAsFloat(ratings);
            }
        }

        return Optional.fromNullable(result);
    }

}
