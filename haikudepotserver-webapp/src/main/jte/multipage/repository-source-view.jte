<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import org.apache.commons.lang3.StringUtils
@import org.haiku.haikudepotserver.multipage.controller.RepositorySourceViewController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.jte.TimestampHelper
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param RepositorySourceViewController.RepositorySourceViewData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.repository_source_view.title", data.code()),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.of("repository-source-view"),
jsLeafnames = List.<String>of(),
content = @`

    <main id="common-main-content-container">

        @if (data.didTriggerImport())
            @template.multipage.common.common-alert-box(
            type = org.haiku.haikudepotserver.multipage.model.AlertType.INFO,
            content = @`
                <p>${internationalizationSupplier.getMessage("mp.repository_source_view.did_trigger_import")}</p>
            `)
        @endif

        <dl>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_view.code.title")}</dt>
            <dd><code>${data.code()}</code></dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_view.repository.title")}</dt>
            <dd>
                <a href="${data.viewRepositoryUriComponents().toUriString()}">
                    <code>${data.repositoryCode()}</code>
                </a>
            </dd>
            <dt>${internationalizationSupplier.getMessage("mp.gen.active")}</dt>
            <dd>@template.multipage.common.common-active-indicator(value = data.active())</dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_view.identifier.title")}</dt>
            <dd>
                @if (StringUtils.isNotBlank(data.identifier()))
                    <code>${data.identifier()}</code>
                @else
                    <span class="common-muted">
                    ${internationalizationSupplier.getMessage("mp.repository_source_view.identifier.missing")}
                    </span>
                @endif
            </dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_view.architecture.title")}</dt>
            <dd>
                @if (StringUtils.isNotBlank(data.architectureCode()))
                    <code>${data.architectureCode()}</code>
                @else
                    <span class="common-muted">
                    ${internationalizationSupplier.getMessage("mp.repository_source_view.architecture.missing")}
                    </span>
                @endif
            </dd>
            <dt>${internationalizationSupplier.getMessage("mp.repository_source_view.last_import_timestamp.title")}</dt>
            <dd>
                @if (null != data.lastImportTimestamp())
                    ${TimestampHelper.formatTimestamp(data.lastImportTimestamp())}
                @else
                    <span class="common-muted">
                    ${internationalizationSupplier.getMessage("mp.repository_source_view.last_import_timestamp.missing")}
                    </span>
                @endif
            </dd>
            @if (data.canEdit())
                <dt>${internationalizationSupplier.getMessage("mp.repository_source_view.forced_internal_base_url.title")}</dt>
                <dd>
                    @if (null != data.forcedInternalBaseUrl())
                        <code>repositorySource.forcedInternalBaseUrl()</code>
                    @else
                        <span class="common-muted">
                        ${internationalizationSupplier.getMessage("mp.repository_source_view.forced_internal_base_url.missing")}
                        </span>
                    @endif
                </dd>
            @endif
            @if (data.canEdit())
                <dt>${internationalizationSupplier.getMessage("mp.repository_source_view.expected_update_frequency_hours.title")}</dt>
                <dd>
                    @if (null != data.expectedUpdateFrequencyHours())
                        ${internationalizationSupplier.getMessage(
                            "mp.repository_source_view.expected_update_frequency_hours.value",
                            data.expectedUpdateFrequencyHours())}
                    @else
                        <span class="common-muted">
                        ${internationalizationSupplier.getMessage("mp.repository_source_view.expected_update_frequency_hours.missing")}
                        </span>
                    @endif
                </dd>
            @endif
        </dl>

        <h3>${internationalizationSupplier.getMessage("mp.repository_source_view.extra_identifiers_list.title")}</h3>

        @if (data.extraIdentifiers().isEmpty())
            <p>${internationalizationSupplier.getMessage("mp.repository_source_view.extra_identifiers_list.missing")}</p>
        @else
            <table class="common-table" id="repository-source-view-repository-extra-identifiers-table">
                <thead>
                <tr>
                    <th>${internationalizationSupplier.getMessage("mp.repository_source_view.extra_identifiers_list.table.identifier.title")}</th>
                </tr>
                </thead>
                <tbody>
                @for (String extraIdentifier : data.extraIdentifiers())
                    <tr>
                        <td><code>${extraIdentifier}</code></td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif

        <h3>${internationalizationSupplier.getMessage("mp.repository_source_view.mirrors_list.title")}</h3>

        @if (data.repositorySourceMirrors().isEmpty())
            <p>${internationalizationSupplier.getMessage("mp.repository_source_view.mirrors_list.missing")}</p>
        @else
            <table class="common-table" id="repository-source-view-repository-source-mirror-table">
                <thead>
                <tr>
                    <th>${internationalizationSupplier.getMessage("mp.repository_source_view.mirrors_list.table.is_primary.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.repository_source_view.mirrors_list.table.active.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.repository_source_view.mirrors_list.table.country_code.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.repository_source_view.mirrors_list.table.base_url.title")}</th>
                </tr>
                </thead>
                <tbody>
                @for (RepositorySourceViewController.RepositorySourceMirror repositorySourceMirror : data.repositorySourceMirrors())
                    <tr>
                        <td>@template.multipage.common.common-boolean-indicator(value = repositorySourceMirror.isPrimary())</td>
                        <td>@template.multipage.common.common-active-indicator(value = repositorySourceMirror.active())</td>
                        <td>
                            <a href="${repositorySourceMirror.viewUriComponents().toUriString()}">
                                <code>${repositorySourceMirror.countryCode()}</code>
                            </a>
                        </td>
                        <td><code>${repositorySourceMirror.baseUrl()}</code></td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif

        @if (data.canImport() || data.canEdit())
            <h3>${internationalizationSupplier.getMessage("mp.gen.actions")}</h3>

            <ul>
                @if (data.canEdit() && data.active())
                    <li>
                        @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositorySourceViewController.OneshotAction.DEACTIVATE.name(),
                        uriComponents = data.oneshotActionUriComponents(),
                        content = @`${internationalizationSupplier.getMessage("mp.repository_source_view.action.deactivate.title")}`)
                    </li>
                @endif
                @if (data.canEdit() && !data.active())
                    <li>
                        @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositorySourceViewController.OneshotAction.REACTIVATE.name(),
                        uriComponents = data.oneshotActionUriComponents(),
                        content = @`${internationalizationSupplier.getMessage("mp.repository_source_view.action.reactivate.title")}`)
                    </li>
                @endif
                @if (data.canImport())
                    <li>
                        @template.multipage.common.common-oneshot-action-link(
                        actionCode = RepositorySourceViewController.OneshotAction.TRIGGER_IMPORT.name(),
                        uriComponents = data.oneshotActionUriComponents(),
                        content = @`${internationalizationSupplier.getMessage("mp.repository_source_view.action.trigger_import.title")}`)
                    </li>
                @endif
                @if (data.canEdit())
                    <li>
                        <a href="${data.addRepositorySourceMirrorUriComponents().toUriString()}">
                            ${internationalizationSupplier.getMessage("mp.repository_source_view.action.add_repository_source_mirror.title")}
                        </a>
                    </li>
                @endif
            </ul>

        @endif

    </main>
`
)