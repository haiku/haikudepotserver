<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.haiku.haikudepotserver.multipage.controller.UserUsageConditionsAgreeController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param UserUsageConditionsAgreeController.AgreeData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.user_usage_conditions_agree.title"),
userAndNavigation = null,
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.<String>of(),
jsLeafnames = List.<String>of(),
showSearch = false,
showUser = false,
content = @`

    <main id="common-main-content-container">

        ${internationalizationSupplier.getPassiveContext("user-usage-conditions-help.html")}

        !{UserUsageConditionsAgreeController.LatestUserUsageConditionsData uucd = data.latestUserUsageConditions();}

        <form method="POST" action="${data.agreeActionUriComponents().toUriString()}" class="common-form">

            <input type="hidden" name="code" value="${data.latestUserUsageConditions().code()}">

            @if (null != data.redirectUri())
                <input type="hidden" name="redirectUri" value="${data.redirectUri()}">
            @endif

            <div>
                <input type="checkbox" name="isAgreed" value="true" checked="${data.form().isAgreed()}">
                <label for="isAgreed">
                    ${internationalizationSupplier.getMessage("mp.user_usage_conditions_agree.is_agreed.label", uucd.code())}
                    (<a href="${uucd.uriComponents().toUriString()}">${internationalizationSupplier.getMessage("mp.gen.view")}</a>)
                </label>
                @template.multipage.common.common-validation-errors(internationalizationSupplier = internationalizationSupplier, failures = data.form().isAgreedFailures())
            </div>

            <div>
                <input type="checkbox" name="isAgreedMinimumAge" value="true" checked="${data.form().isAgreedMinimumAge()}">
                <label for="isAgreedMinimumAge">${internationalizationSupplier.getMessage("mp.user_usage_conditions_agree.minimum_age.label", uucd.minimumAge())}</label>
                @template.multipage.common.common-validation-errors(internationalizationSupplier = internationalizationSupplier, failures = data.form().isAgreedMinimumAgeFailures())
            </div>

            <div class="common-form-submit">
                <button name="action" value="LOGOUT">${internationalizationSupplier.getMessage("mp.user_usage_conditions_agree.action.logout.title")}</button>
                <button name="action" value="AGREE">${internationalizationSupplier.getMessage("mp.user_usage_conditions_agree.action.agree.title")}</button>
            </div>

        </form>

    </main>
`
)