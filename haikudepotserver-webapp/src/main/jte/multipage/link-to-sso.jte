<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.haiku.haikudepotserver.multipage.controller.LinkToSsoController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.AlertType
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param org.haiku.haikudepotserver.multipage.controller.LinkToSsoController.LinkToSsoData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.link_to_sso.title"),
userAndNavigation = null,
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.<String>of(),
jsLeafnames = List.<String>of(),
showSearch = false,
showUser = false,
content = @`

    <main id="common-main-content-container">

        @template.multipage.common.common-alert-box(
        type = org.haiku.haikudepotserver.multipage.model.AlertType.MUTED,
        content = @`
            <p>
                This page will allow you to link your existing HaikuDepotServer (HDS) user with
                your Haiku SSO identity. This is in preparation for HDS moving to use SSO for
                user-management. A user should only proceed with this process if one of the
                following criteria applies;
            </p>

            <ul>
                <li>
                    The user has any administrative rights in HDS.
                </li>
                <li>
                    The user has provided any content; translations, screenshots, icons etc...
                </li>
                <li>
                    The user has supplied any user ratings.
                </li>
            </ul>

            <p>
                Any users which don't meet these criteria don't need to be concerned with this
                process because later, a new user will be created for them the first time they
                login using SSO.
            </p>

            <ul>
                <li>
                    See the latest
                    <a href="${data.userUsageConditionsUriComponents().toUriString()}" target="_blank">
                        user usage conditions
                    </a>
                    for information about this data is used.
                </li>
            </ul>

        `)

        <H2>Step 1 - Login with Haiku SSO User</H2>

        @if (data.step() == LinkToSsoController.Step.LOGIN_SSO)
            <p>
                <a href="${data.ssoLoginUriComponents().toUriString()}">Login to the Haiku SSO</a>
            </p>
        @else
            @if (data.step().ordinal() > LinkToSsoController.Step.LOGIN_SSO.ordinal())
                <p>
                    @template.multipage.common.common-active-indicator(value = true)
                    Logged in to the Haiku SSO as <code>${data.ssoUser().username()}</code>
                </p>
            @endif
        @endif

        <h2 class="${data.step().ordinal() < LinkToSsoController.Step.LOGIN_USER.ordinal() ? "common-muted": ""}">
            Step 2 - Login with HDS User
        </h2>

        @if (data.step() == LinkToSsoController.Step.LOGIN_USER)

            @if (data.failedLogin())
                @template.multipage.common.common-alert-box(
                type = AlertType.ERROR,
                content = @`Login failed`)
            @endif

            <form method="POST" action="${data.loginActionUriComponents().toUriString()}" class="common-form">

                <div class="common-form-input-container">
                    <label for="nickname">
                        Nickname
                        @template.multipage.common.common-form-required-mark()
                    </label>
                    <input type="text" name="nickname">
                </div>
                <div class="common-form-input-container">
                    <label for="password">
                        Password
                        @template.multipage.common.common-form-required-mark()
                    </label>
                    <input type="password" name="password">
                </div>
                <div class="common-form-submit">
                    <input type="submit" name="submit" value="Authenticate">
                </div>

            </form>

        @else
            @if (data.step().ordinal() > LinkToSsoController.Step.LOGIN_USER.ordinal())
                <p>
                    @template.multipage.common.common-active-indicator(value = true)
                    Logged in to the Haiku SSO as <code>${data.user().nickname()}</code>
                </p>
            @endif
        @endif

        <h2 class="${data.step().ordinal() < LinkToSsoController.Step.CONFIRM.ordinal() ? "common-muted": ""}">
            Step 3 - Confirm Haiku SSO User + HDS User
        </h2>

        @if (data.step() == LinkToSsoController.Step.CONFIRM)
            <p>
                <form method="POST" action="${data.confirmLinkActionUriComponents().toUriString()}">
                    @template.multipage.common.common-post-link(content = @`Confirm linking this Haiku SSO user with the existing HDS user`)
                </form>
            </p>

            <div><small>
                    (See the latest
                    <a href="${data.userUsageConditionsUriComponents().toUriString()}" target="_blank">
                        user usage conditions
                    </a>
                    for information about this data is used.)
            </small></div>
        @else
            @if (data.step().ordinal() > LinkToSsoController.Step.CONFIRM.ordinal())
                <p>
                    @template.multipage.common.common-active-indicator(value = true)
                    Your HDS User is now connected to the Haiku SSO User!
                </p>
            @endif
        @endif

        <h2 class="${data.step().ordinal() < LinkToSsoController.Step.COMPLETE.ordinal() ? "common-muted": ""}">
            Step 4 - Done!
        </h2>

        @if (data.step() == LinkToSsoController.Step.COMPLETE)
            <p>
                @template.multipage.common.common-active-indicator(value = true)
                You're all done; <a href="${data.completeUriComponents().toUriString()}">navigate back to HDS</a>.
            </p>
        @endif

    </main>

`
)