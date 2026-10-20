<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.apache.commons.lang3.StringUtils
@import org.haiku.haikudepotserver.multipage.controller.RepositorySourceMirrorViewController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param RepositorySourceMirrorViewController.RepositorySourceMirrorViewData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.repository_source_mirror_view.title"),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.<String>of(),
jsLeafnames = List.<String>of(),
content = @`

    <main id="common-main-content-container">

        <dl>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.repository_code.title")}</dt>
            <dd>
                <a href="${data.viewRepositoryUriComponents().toUriString()}">
                    <code>${data.repositoryCode()}</code>
                </a>
            </dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.repository_source_code.title")}</dt>
            <dd>
                <a href="${data.viewRepositorySourceUriComponents().toUriString()}">
                    <code>${data.repositorySourceCode()}</code>
                </a>
            </dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.is_primary.title")}</dt>
            <dd>@template.multipage.common.common-boolean-indicator(value = data.isPrimary())</dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.active.title")}</dt>
            <dd>@template.multipage.common.common-active-indicator(value = data.active())</dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.country_code.title")}</dt>
            <dd><code>${data.countryCode()}</code></dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.base_url.title")}</dt>
            <dd><code>${data.baseUrl()}</code></dd>
            @if (StringUtils.isNotBlank(data.description()))
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.description.title")}</dt>
            <dd><code>${data.description()}</code></dd>
            @endif
        </dl>

        @if (data.canEdit())
            <h3>${internationalizationSupplier.getMessage("mp.gen.actions")}</h3>

            <ul>
                @if (data.canEdit() && !data.isPrimary())
                    <li>
                        @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositorySourceMirrorViewController.OneshotAction.SET_PRIMARY.name(),
                        uriComponents = data.oneshotActionUriComponents(),
                        content = @`${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.action.set_primary.title")}`)
                    </li>
                @endif
                @if (data.canEdit() && !data.isPrimary() && data.active())
                    <li>
                        @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositorySourceMirrorViewController.OneshotAction.DEACTIVATE.name(),
                        uriComponents = data.oneshotActionUriComponents(),
                        content = @`${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.action.deactivate.title")}`)
                    </li>
                @endif
                @if (data.canEdit() && !data.active())
                    <li>
                        @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositorySourceMirrorViewController.OneshotAction.REACTIVATE.name(),
                        uriComponents = data.oneshotActionUriComponents(),
                        content = @`${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.action.reactivate.title")}`)
                    </li>
                @endif
                @if (data.canEdit() && !data.isPrimary())
                    <li>
                        @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositorySourceMirrorViewController.OneshotAction.REMOVE.name(),
                        uriComponents = data.oneshotActionUriComponents(),
                        content = @`${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.action.remove.title")}`)
                    </li>
                @endif
                @if (data.canEdit())
                    <li>
                        <a href="${data.editUriComponents().toUriString()}">
                            ${internationalizationSupplier.getMessage("mp.repository_source_mirror_view.action.edit.title")}
                        </a>
                    </li>
                @endif
            </ul>

        @endif

    </main>
`
)