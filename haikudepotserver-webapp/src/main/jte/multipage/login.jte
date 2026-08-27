<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.AlertType
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes
@import org.haiku.haikudepotserver.support.web.WebConstants

@param org.haiku.haikudepotserver.multipage.controller.LoginController.LoginData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.login.title"),
userAndNavigation = null,
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.of("login"),
jsLeafnames = List.<String>of(),
showSearch = false,
showUser = false,
content = @`

    <main id="common-main-content-container">

        @if (data.didLogout())
            @template.multipage.common.common-alert-box(
                type = AlertType.INFO,
                content = @`${internationalizationSupplier.getMessage("mp.login.logout.description")}`)
        @endif
        @if (data.didError())
            @template.multipage.common.common-alert-box(
                type = AlertType.ERROR,
                content = @`${internationalizationSupplier.getMessage("mp.login.error.description")}`)
        @endif

        <form method="POST" action="${data.loginUrl()}" class="common-form">

            @if (null != data.redirectUri())
            <input type="hidden" name="${WebConstants.KEY_REDIRECT_URI}" value="${data.redirectUri()}">
            @endif

            <div class="common-form-input-container">
                <label for="username">
                    ${internationalizationSupplier.getMessage("mp.login.username.label")}
                    @template.multipage.common.common-form-required-mark()
                </label>
                <input type="text" name="username">
            </div>
            <div class="common-form-input-container">
                <label for="password">
                    ${internationalizationSupplier.getMessage("mp.login.password.label")}
                    @template.multipage.common.common-form-required-mark()
                </label>
                <input type="password" name="password">
            </div>
            <div class="common-form-submit">
                <div id="login-cookie-note">${internationalizationSupplier.getMessage("mp.login.cookies.description")}</div>
                <input type="submit" name="submit" value="${internationalizationSupplier.getMessage("mp.login.action.login.title")}">
            </div>
        </form>

        <h3>${internationalizationSupplier.getMessage("mp.gen.actions")}</h3>

        <ul>
            <li>
                <a href="${data.createUserUriComponents().toUriString()}">${internationalizationSupplier.getMessage("mp.login.action.create_user.title")}</a>
            </li>
            <li>
                <a href="${data.forgotPasswordUriComponents().toUriString()}">${internationalizationSupplier.getMessage("mp.login.action.forgot_password.title")}</a>
            </li>
            <li>
                <a href="${data.userUsageConditionsUriComponents().toUriString()}">${internationalizationSupplier.getMessage("mp.login.action.user_usage_conditions.title")}</a>
            </li>
        </ul>

    </main>

`
)