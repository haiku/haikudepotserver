<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.haiku.haikudepotserver.multipage.controller.UserViewController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.jte.TimestampHelper
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param UserViewController.UserViewData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.user_view.title", data.nickname()),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.<String>of(),
jsLeafnames = List.<String>of(),
content = @`

    <main id="common-main-content-container">

        <dl>
            <dt>${internationalizationSupplier.getMessage("mp.user_view.nickname.title")}</dt>
            <dd><code>${data.nickname()}</code></dd>
            <dt>${internationalizationSupplier.getMessage("mp.gen.active")}</dt>
            <dd>@template.multipage.common.common-active-indicator(value = data.active())</dd>
            @if (data.isRoot())
                <dt>${internationalizationSupplier.getMessage("mp.user_view.is_root.title")}</dt>
                <dd>&checkmark;</dd>
            @endif
            <dt>${internationalizationSupplier.getMessage("mp.user_view.create_timestamp.title")}</dt>
            <dd>${TimestampHelper.formatTimestamp(data.createTimestamp())}</dd>
            <dt>${internationalizationSupplier.getMessage("mp.user_view.modify_timestamp.title")}</dt>
            <dd>${TimestampHelper.formatTimestamp(data.modifyTimestamp())}</dd>
            <dt>${internationalizationSupplier.getMessage("mp.user_view.last_authentication_timestamp.title")}</dt>
            <dd>
                @if (null == data.lastAuthenticationTimestamp())
                    ${internationalizationSupplier.getMessage("mp.user_view.last_authentication_timestamp.none")}
                @else
                    ${TimestampHelper.formatTimestampHourAccurate(data.lastAuthenticationTimestamp())}
                @endif
            </dd>
        </dl>

        <h3>${internationalizationSupplier.getMessage("mp.user_view.user_usage_conditions.title")}</h3>

        @if (data.isRoot())
            <p>${internationalizationSupplier.getMessage("mp.user_view.user_usage_conditions.root_excluded.description")}</p>
        @else
            !{UserViewController.UserUsageConditionsAgreement agreement = data.userUsageConditionsAgreement();}

            @if (null == agreement)
                <p>${internationalizationSupplier.getMessage("mp.user_view.user_usage_conditions.none.description")}</p>
            @else
                <dl>
                    <dt>${internationalizationSupplier.getMessage("mp.user_view.user_usage_conditions_at.title")}</dt>
                    <dd>${TimestampHelper.formatTimestamp(agreement.timestampAgreed())}</dd>
                    <dt>${internationalizationSupplier.getMessage("mp.user_view.user_usage_conditions_code.title")}</dt>
                    <dd><code>${agreement.userUsageConditionsCode()}</code></dd>
                    <dt>${internationalizationSupplier.getMessage("mp.user_view.user_usage_conditions_minimum_age.title")}</dt>
                    <dd>${internationalizationSupplier.getMessage("mp.user_view.user_usage_conditions_minimum_age.value", agreement.minimumAge())}</dd>
                </dl>
            @endif

            <ul>
                @if (null != agreement)
                <li>
                    <a href="${agreement.userUsageConditionsUriComponents().toUriString()}">
                        ${internationalizationSupplier.getMessage("mp.user_view.action.view_agreed_user_usage_conditions.title")}
                    </a>
                </li>
                @endif
                @if ((null == agreement || !agreement.isLatest()) && data.isAuthenticatedUser())
                <li>
                    <a href="${data.agreeLatestUserUsageConditionsUriComponents().toUriString()}">
                        ${internationalizationSupplier.getMessage("mp.user_view.action.agree_latest_user_usage_conditions.title")}
                    </a>
                </li>
                @endif
            </ul>
        @endif

        <h3>${internationalizationSupplier.getMessage("mp.gen.actions")}</h3>

        <ul>
            @if (data.isAuthenticatedUser())
                <li><a href="${data.logoutUriComponents().toUriString()}">${internationalizationSupplier.getMessage("mp.user_view.action.logout.title")}</a></li>
            @endif
        </ul>

    </main>
`
)