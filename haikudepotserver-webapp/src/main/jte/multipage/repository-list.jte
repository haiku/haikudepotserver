<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import org.haiku.haikudepotserver.multipage.controller.RepositoryListController
@import org.haiku.haikudepotserver.multipage.jte.*
@import org.haiku.haikudepotserver.multipage.MultipageConstants
@import java.util.List
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param org.haiku.haikudepotserver.multipage.controller.RepositoryListController.RepositoryListData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.repository_list.title"),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.of("repository-list"),
jsLeafnames = List.<String>of(),
content = @`
    <main id="common-main-content-container">

        @if(data.total() > 0)
            <div id="repository-list-pagination">
                <div class="common-muted">
                    ${internationalizationSupplier.getMessage("mp.repository_list.result.found_description", data.total())}
                </div>
                <div>
                    @template.multipage.common.common-pagination(
                    internationalizationSupplier = internationalizationSupplier,
                    uriComponents = data.uriComponents(),
                    offsetQueryParameter = MultipageConstants.KEY_OFFSET,
                    pagination = data.pagination(),
                    webResourcePathPrefixes = webResourcePathPrefixes
                    )
                </div>
            </div>

            <table class="common-table" id="repository-list-table">
                <thead>
                <tr>
                    <th>${internationalizationSupplier.getMessage("mp.gen.active")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.repository_list.table.code.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.repository_list.table.name.title")}</th>
                </tr>
                </thead>
                <tbody>
                @for (RepositoryListController.Repository repository : data.repositories())
                    <tr>
                        <td>@template.multipage.common.common-active-indicator(value = repository.active())</td>
                        <td>
                            <a href="${repository.viewUriComponents().toUriString()}">
                                <code>${repository.code()}</code>
                            </a>
                        </td>
                        <td>${repository.name()}</td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @else
            <h2>${internationalizationSupplier.getMessage("mp.repository_list.no_results.title")}</h2>
            <p>${internationalizationSupplier.getMessage("mp.repository_list.no_results.description")}</p>
        @endif

        @if (!data.includeInactive() && data.canListInactive())

            <h3>${internationalizationSupplier.getMessage("mp.gen.actions")}</h3>

            <ul>
                @if (!data.includeInactive() && data.canListInactive())
                    <li>
                        <a href="${data.includeInactiveUriComponents().toUriString()}">
                            ${internationalizationSupplier.getMessage("mp.repository_list.action.include_inactive.title")}
                        </a>
                    </li>
                @endif
            </ul>
        @endif

    </main>
`
)