/*
 * Copyright 2013-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.user;

public class GetUserResult {

    public String nickname;

    public String email;

    public Boolean active;

    public Boolean isRoot;

    public Long createTimestamp;

    public Long modifyTimestamp;

    public String naturalLanguageCode;

    /**
     * @since 2018-12-30
     */

    public Long lastAuthenticationTimestamp;

    /**
     * <p>This relates to the user's required understanding of the terms of
     * use.</p>
     * @since 2019-03-10
     */

    public UserUsageConditionsAgreement userUsageConditionsAgreement;

    /**
     * @since 2019-03-10
     */

    public static class UserUsageConditionsAgreement {

        public Long timestampAgreed;

        /**
         * <p>The code of the conditions agreed to.</p>
         */

        public String userUsageConditionsCode;

        /**
         * <p>Are the terms that the user agreed to current with the most
         * recent wording.</p>
         */

        public Boolean isLatest;

    }

}
