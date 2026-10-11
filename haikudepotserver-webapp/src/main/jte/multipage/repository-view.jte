<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.haiku.haikudepotserver.multipage.controller.RepositoryViewController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.jte.TimestampHelper
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param RepositoryViewController.RepositoryViewData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.repository_view.title", data.name()),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.of("repository-view"),
jsLeafnames = List.<String>of(),
content = @`

    <main id="common-main-content-container">

        @if (data.didTriggerImport())
            @template.multipage.common.common-alert-box(
            type = org.haiku.haikudepotserver.multipage.model.AlertType.INFO,
            content = @`
                <p>${internationalizationSupplier.getMessage("mp.repository_view.did_trigger_import")}</p>
            `)
        @endif

        <dl>
            <dt>${internationalizationSupplier.getMessage("mp.repository_view.code.title")}</dt>
            <dd><code>${data.code()}</code></dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_view.name.title")}</dt>
            <dd>${data.name()}</dd>
            <dt>${internationalizationSupplier.getMessage("mp.gen.active")}</dt>
            <dd>@template.multipage.common.common-active-indicator(value = data.active())</dd>
            @if (data.informationUriComponents() != null)
            <dt>${internationalizationSupplier.getMessage("mp.repository_view.information_url.title")}</dt>
            <dd><a href="${data.informationUriComponents().toUriString()}">${data.informationUriComponents().toUriString()}</a></dd>
            @endif
            <dt>${internationalizationSupplier.getMessage("mp.repository_view.create_timestamp.title")}</dt>
            <dd>${TimestampHelper.formatTimestamp(data.createTimestamp())}</dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_view.modify_timestamp.title")}</dt>
            <dd>${TimestampHelper.formatTimestamp(data.modifyTimestamp())}</dd>

            @if (data.canEdit())
                <dt>${internationalizationSupplier.getMessage("mp.repository_view.has_password.title")}</dt>
                <dd>@template.multipage.common.common-active-indicator(value = data.hasPassword())</dd>
            @endif
        </dl>

        <h2>${internationalizationSupplier.getMessage("mp.repository_view.repositorysource_list.title")}</h2>

        @if (data.repositorySources().isEmpty())
            <p>${internationalizationSupplier.getMessage("mp.repository_view.repositorysource_list.no_results.description")}</p>
        @else

            <table class="common-table" id="repository-view-repository-source-table">
                <thead>
                <tr>
                    <th>${internationalizationSupplier.getMessage("mp.gen.active")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.repository_view.repositorysource_list.table.code.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.repository_view.repositorysource_list.table.last_import_timestamp.title")}</th>
                </tr>
                </thead>
                <tbody>
                @for (RepositoryViewController.RepositorySource repositorySource : data.repositorySources())
                    <tr>
                        <td>@template.multipage.common.common-active-indicator(value = repositorySource.active())</td>
                        <td><code>${repositorySource.code()}</code></td>
                        <td>${TimestampHelper.formatTimestamp(repositorySource.lastImportTimestamp())}</td>
                    </tr>
                @endfor
                </tbody>
            </table>

        @endif

        @if (data.canImport() || data.canEdit())
        <h3>${internationalizationSupplier.getMessage("mp.gen.actions")}</h3>

        <ul>
            @if (data.canEdit() && data.hasPassword())
                <li>
                    @template.multipage.common.common-oneshot-action-link(
                    actionCode = RepositoryViewController.OneshotAction.DELETE_PASSWORD.name(),
                    uriComponents = data.oneshotActionUriComponents(),
                    content = @`${internationalizationSupplier.getMessage("mp.repository_view.action.delete_password.title")}`)
                </li>
            @endif
            @if (data.canEdit() && data.active())
                <li>
                    @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositoryViewController.OneshotAction.DEACTIVATE.name(),
                    uriComponents = data.oneshotActionUriComponents(),
                    content = @`${internationalizationSupplier.getMessage("mp.repository_view.action.deactivate.title")}`)
                </li>
            @endif
            @if (data.canEdit() && !data.active())
                <li>
                    @template.multipage.common.common-oneshot-action-link(
                    actionCode = RepositoryViewController.OneshotAction.REACTIVATE.name(),
                    uriComponents = data.oneshotActionUriComponents(),
                    content = @`${internationalizationSupplier.getMessage("mp.repository_view.action.reactivate.title")}`)
                </li>
            @endif
            @if (data.canImport())
                <li>
                    @template.multipage.common.common-oneshot-action-link(
                    actionCode = RepositoryViewController.OneshotAction.TRIGGER_IMPORT.name(),
                    uriComponents = data.oneshotActionUriComponents(),
                    content = @`${internationalizationSupplier.getMessage("mp.repository_view.action.trigger_import.title")}`)
                </li>
            @endif
        </ul>
        @endif

    </main>
`
)