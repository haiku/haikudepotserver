/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.user;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.support.LikeHelper;
import org.haiku.haikudepotserver.user.model.UserSearchSpecification;
import org.haiku.haikudepotserver.user.model.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);

    private ObjectSelect<User> prepareObjectSelect(UserSearchSpecification search) {
        Preconditions.checkNotNull(search);

        ObjectSelect<User> objectSelect = ObjectSelect.query(User.class);

        if (!Strings.isNullOrEmpty(search.getExpression())) {
            switch (search.getExpressionType()) {

                case CONTAINS:
                    objectSelect = objectSelect.where(User.NICKNAME.likeIgnoreCase(
                            "%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%"));
                    break;

                default:
                    throw new IllegalStateException("unknown expression type " + search.getExpressionType().name());

            }
        }

        if (!search.getIncludeInactive()) {
            objectSelect = objectSelect.where(User.ACTIVE.isTrue());
        }

        return objectSelect;
    }

    /**
     * <p>Undertakes a search for users.</p>
     */

    @Override
    public List<User> search(
            ObjectContext context,
            UserSearchSpecification searchSpecification) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(searchSpecification);
        Preconditions.checkState(searchSpecification.getOffset() >= 0);
        Preconditions.checkState(searchSpecification.getLimit() > 0);

        return prepareObjectSelect(searchSpecification)
                .offset(searchSpecification.getOffset())
                .limit(searchSpecification.getLimit())
                .orderBy(User.NICKNAME.asc())
                .select(context);
    }

    /**
     * <p>Find out the total number of results that would be yielded from
     * a search if the search were not constrained.</p>
     */

    public long total(
            ObjectContext context,
            UserSearchSpecification searchSpecification) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(searchSpecification);
        return prepareObjectSelect(searchSpecification).count().selectFirst(context);
    }

}
