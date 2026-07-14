@import org.apache.commons.lang3.StringUtils
@import org.haiku.haikudepotserver.multipage.controller.HomeController
@import org.haiku.haikudepotserver.multipage.jte.*
@import org.haiku.haikudepotserver.multipage.model.PkgCategory
@import org.haiku.haikudepotserver.multipage.MultipageConstants
@import java.util.List
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier

@param org.haiku.haikudepotserver.multipage.controller.HomeController.HomeData data
@param InternationalizationSupplier internationalizationSupplier

@template.multipage.common.common-page-mantle(
    title = internationalizationSupplier.getMessage("mp.home.title"),
menuGroups = data.menuGroups(),
internationalizationSupplier = internationalizationSupplier,
cssLeafnames = List.of("home"),
jsLeafnames = List.of("home"),
content = @`

    <main id="common-main-content-container">
    <div id="home-filters">
        <div>
            <form method="GET" action="${data.searchUrl()}">
                @template.multipage.common.common-hidden-inputs(parameters = data.relayParameters())

                <span>${internationalizationSupplier.getMessage("mp.home.filters.category.title")}</span>
                <select
                        name="${MultipageConstants.KEY_PKGCATEGORYCODE}"
                        id="home-filters-category-select">
                    <option
                            value=""
                            selected="">${internationalizationSupplier.getMessage("mp.gen.all")}</option>
                    @for (PkgCategory pkgCategory : data.criteria().referenceData().pkgCategories())
                        <option
                                value="${pkgCategory.code()}"
                                selected="${null != data.criteria().pkgCategory() && pkgCategory.code().equals(data.criteria().pkgCategory().code())}">${pkgCategory.name()}</option>
                    @endfor
                </select>
            </form>
        </div>
        <div>
            @template.multipage.common.common-pagination(
                internationalizationSupplier = internationalizationSupplier,
                uriComponents = data.uriComponents(),
                offsetQueryParameter = MultipageConstants.KEY_OFFSET,
                pagination = data.pagination()
            )
        </div>
    </div>

    @if (data.pkgVersions().isEmpty())

        <h2>${internationalizationSupplier.getMessage("mp.home.no_results.title")}</h2>
        <p>${internationalizationSupplier.getMessage("mp.home.no_results.description")}</p>

    @else

        <div id="home-pkgs-container">

            @for(HomeController.PkgVersion pkgVersion : data.pkgVersions())
                <div class="home-pkg-container">

                    @template.multipage.common.common-pkg-icon(
                    pkgName = pkgVersion.pkgName(),
                    size = 64,
                    versionDifferentiator = pkgVersion.versionCreateTimestamp(),
                    isNativeDesktop = pkgVersion.isNativeDesktop())

                    <div class="home-pkg-title">
                        <a href="${pkgVersion.viewUriComponents().toUriString()}">
                        @if(StringUtils.isNotBlank(pkgVersion.title()))
                            ${pkgVersion.title()}
                        @else
                            <code>${pkgVersion.pkgName()}</code>
                        @endif
                        </a>
                    </div>
                    <div class="home-pkg-summary">${pkgVersion.summary()}</div>
                    ${RatingHelper.stars(pkgVersion.derivedRating(), 16)}
                </div>
            @endfor

        </div>

    @endif
    </main>
`
)