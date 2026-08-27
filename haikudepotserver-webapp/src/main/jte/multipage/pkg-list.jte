<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import org.apache.commons.lang3.StringUtils
@import org.haiku.haikudepotserver.multipage.controller.PkgListController.PkgVersion
@import org.haiku.haikudepotserver.multipage.controller.PkgListController
@import org.haiku.haikudepotserver.multipage.jte.*
@import org.haiku.haikudepotserver.multipage.jte.RatingHelper
@import org.haiku.haikudepotserver.multipage.jte.TextHelper
@import org.haiku.haikudepotserver.multipage.model.Architecture
@import org.haiku.haikudepotserver.multipage.model.PkgCategory
@import org.haiku.haikudepotserver.multipage.model.Repository
@import org.haiku.haikudepotserver.multipage.MultipageConstants
@import java.util.List
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param org.haiku.haikudepotserver.multipage.controller.PkgListController.PkgListData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.pkg_list.title"),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.of("pkg-list"),
jsLeafnames = List.of("pkg-list"),
content = @`
        <main id="common-main-content-container">

        <form method="GET" action="${data.searchUrl()}" id="pkg-list-filters">

            @template.multipage.common.common-hidden-inputs(parameters = data.relayParameters())

            <div id="pkg-list-filters-search">
                <input type="search" placeholder="Search" name="${MultipageConstants.KEY_SEARCHEXPRESSION}"
                       value="${data.criteria().searchExpression()}">
                <button>${internationalizationSupplier.getMessage("mp.pkg_list.search_button.title")}</button>
            </div>

            @template.multipage.common.common-disclosure(
            id = "pkg-list-filters-options-disclosure",
            hiddenTitle = "Search filter",
            shownInputName = PkgListController.KEY_SHOWFILTERS,
            shown = data.criteria().showFilters(),
            content = @`
                <div id="pkg-list-filters-options">
                    <div>
                        <div class="common-muted">${internationalizationSupplier.getMessage("mp.pkg_list.filters.repositories.title")}</div>
                        <div>
                            <select name="${PkgListController.KEY_REPOSITORIESCODES}" multiple>
                                @for (Repository repository : data.criteria().referenceData().repositories())
                                    <option
                                            selected="${data.criteria().repositories().contains(repository)}"
                                            value="${repository.code()}">${repository.name()}</option>
                                @endfor
                            </select>
                        </div>
                    </div>
                    <div>
                        <div class="common-muted">${internationalizationSupplier.getMessage("mp.pkg_list.filters.sorting_and_criteria.title")}</div>
                        <div>
                            <select name="${PkgListController.KEY_VIEWCRITERIATYPECODE}"
                                    id="pkg-list-filters-sorting-selector">
                                @for (PkgListController.ViewCriteriaType viewCriteriaType : data.criteria().viewCriteriaTypes())
                                    <option
                                            selected="${viewCriteriaType == data.criteria().viewCriteriaType()}"
                                            value="${viewCriteriaType.name()}">${internationalizationSupplier.getMessage(viewCriteriaType.titleKey())}</option>
                                @endfor
                            </select>
                        </div>
                    </div>
                    <div>
                        <div class="common-muted">${internationalizationSupplier.getMessage("mp.gen.architecture.title")}</div>
                        <select name="${MultipageConstants.KEY_ARCHITECTURECODE}">
                            @for (Architecture architecture : data.criteria().referenceData().architecturesExcludingPseudo())
                                <option
                                        value="${architecture.code()}"
                                        selected="${architecture.code().equals(data.criteria().architecture().code())}">${architecture.code()}</option>
                            @endfor
                        </select>
                    </div>
                    <div id="pkg-list-filters-category-container"
                         class="${!data.showCategoryFilter() ? "common-hidden" : ""}">
                        <div class="common-muted">${internationalizationSupplier.getMessage("mp.pkg_list.filters.category.title")}</div>
                        <select name="${MultipageConstants.KEY_PKGCATEGORYCODE}">
                            @for (PkgCategory pkgCategory : data.criteria().referenceData().pkgCategories())
                                <option
                                        value="${pkgCategory.code()}"
                                        selected="${pkgCategory.code().equals(data.criteria().pkgCategory().code())}">${pkgCategory.name()}</option>
                            @endfor
                        </select>
                    </div>
                    <div>
                        <div class="common-muted">${internationalizationSupplier.getMessage("mp.pkg_list.filters.options.title")}</div>
                        <div><input
                                    type="checkbox"
                                    name="${PkgListController.KEY_INCLUDEDEVELOPER}"
                                    value="true"
                                    checked="${data.criteria().includeDeveloper()}"> ${internationalizationSupplier.getMessage("mp.pkg_list.filters.include_developer")}
                        </div>
                        <div><input
                                    type="checkbox"
                                    name="${PkgListController.KEY_ONLYDESKTOP}"
                                    value="true"
                                    checked="${data.criteria().onlyDesktop()}"> ${internationalizationSupplier.getMessage("mp.pkg_list.filters.only_desktop")}
                        </div>
                        <div><input
                                    type="checkbox"
                                    name="${PkgListController.KEY_ONLYNATIVEDESKTOP}"
                                    value="true"
                                    checked="${data.criteria().onlyNativeDesktop()}"> ${internationalizationSupplier.getMessage("mp.pkg_list.filters.only_native_desktop")}
                        </div>
                    </div>
                </div>
            `)
        </form>

        @if(data.total() > 0)
            <div id="pkg-list-pagination">
                <div class="common-muted">
                    ${internationalizationSupplier.getMessage("mp.pkg_list.result.found_description", data.total())}
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

            <table class="common-table" id="pkg-list-table">
                <thead>
                <tr>
                    <th></th>
                    <th></th>
                    <th>${internationalizationSupplier.getMessage("mp.pkg_list.table.payload_length.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.pkg_list.table.rating.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.pkg_list.table.version.title")}</th>
                    <th>${internationalizationSupplier.getMessage("mp.pkg_list.table.version_view_counter.title")}</th>
                </tr>
                </thead>
                <tbody>
                @for (PkgVersion pkgVersion : data.pkgVersions())
                    <tr>
                        <td>
                            @template.multipage.common.common-pkg-icon(
                                pkgName = pkgVersion.pkgName(),
                                size = 32,
                                versionDifferentiator = pkgVersion.versionCreateTimestamp(),
                                webResourcePathPrefixes = webResourcePathPrefixes)
                        </td>
                        <td>
                            <div>
                                <a href="${pkgVersion.viewUriComponents().toUriString()}">
                                @if (StringUtils.isNotEmpty(pkgVersion.title()))
                                    ${TextHelper.highlightedHtmlFormattedText(pkgVersion.title(), data.criteria().searchExpression())}
                                @else
                                    <code>${TextHelper.highlightedHtmlFormattedText(pkgVersion.pkgName(), data.criteria().searchExpression())}</code>
                                @endif
                                </a>
                                @if (pkgVersion.isNativeDesktop())
                                    <img src="${webResourcePathPrefixes.img()}native.svg" width="16px" height="16px">
                                @endif
                            </div>
                            <div class="common-muted">
                                <small>${TextHelper.highlightedHtmlFormattedText(pkgVersion.summaryPlus(), data.criteria().searchExpression())}</small>
                            </div>
                        </td>
                        <td>
                            @if (null != pkgVersion.payloadLength())
                                ${internationalizationSupplier.formatDecimal(pkgVersion.payloadLength().quantity(), 1)}
                                ${pkgVersion.payloadLength().unit().getSuffix()}
                            @endif
                        </td>
                        <td>
                            <div class="rating-stars-container">
                                ${RatingHelper.stars(pkgVersion.derivedRating(), 16)}
                            </div>
                        </td>
                        <td>
                            <div>${pkgVersion.version().toString()}</div>
                            <div class="common-muted">
                                <small>${TimestampHelper.formatTimestamp(pkgVersion.versionCreateTimestamp())}</small>
                            </div>
                        </td>
                        <td>
                            ${pkgVersion.viewCounter()}
                        </td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @else
            <h2>${internationalizationSupplier.getMessage("mp.pkg_list.no_results.title")}</h2>
            <p>${internationalizationSupplier.getMessage("mp.pkg_list.no_results.description")}</p>
        @endif

        </main>

`
)