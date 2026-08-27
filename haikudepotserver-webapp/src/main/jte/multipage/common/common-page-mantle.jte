<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import gg.jte.Content
@import org.haiku.haikudepotserver.multipage.controller.BannerSearchController
@import org.haiku.haikudepotserver.multipage.model.MenuGroup
@import java.util.Collection
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.MultipageConstants
@import org.haiku.haikudepotserver.multipage.model.UserAndNavigation
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param Collection<MenuGroup> menuGroups = null
@param UserAndNavigation userAndNavigation
@param Collection<String> cssLeafnames
@param Collection<String> jsLeafnames
@param Content content
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes
@param String title
@param boolean showSearch = true
@param boolean showUser = true

<!DOCTYPE html>
<html>
<head>

    <title>${title}</title>

    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <link rel="icon" type="image/png" href="${webResourcePathPrefixes.img()}haikudepot16.png" sizes="16x16">
    <link rel="icon" type="image/png" href="${webResourcePathPrefixes.img()}haikudepot32.png" sizes="32x32">
    <link rel="icon" type="image/png" href="${webResourcePathPrefixes.img()}haikudepot64.png" sizes="64x64">
    <link rel="icon" type="image/svg" href="${webResourcePathPrefixes.img()}haikudepot.svg">

    <link rel="search" type="application/opensearchdescription+xml" title="Haiku Depot" href="/__pkgsearch/opensearch.xml">

    <link rel="stylesheet" type="text/css" href="${webResourcePathPrefixes.css()}common.css">
    <link rel="stylesheet" type="text/css" href="${webResourcePathPrefixes.css()}banner.css">
    <link rel="stylesheet" type="text/css" href="${webResourcePathPrefixes.css()}menu.css">

    @for (String cssLeafname : cssLeafnames)
        <link rel="stylesheet" type="text/css" href="${webResourcePathPrefixes.css()}${cssLeafname}.css">
    @endfor

    <script src="${webResourcePathPrefixes.js()}common.js"></script>
    <script src="${webResourcePathPrefixes.js()}menu.js"></script>

    @for (String jsLeafname : jsLeafnames)
        <script src="${webResourcePathPrefixes.js()}${jsLeafname}.js"></script>
    @endfor

</head>
<body>

@template.multipage.common.common-svg-sprites-bi()

<header>

    @if (null != menuGroups)
    <div id="header-banner-menu-toggle">
        @template.multipage.common.common-svg-sprite-use(width = 24, height = 24, symbolId = "bi-three-dots-vertical", id="header-banner-menu-toggle-off-icon")
        @template.multipage.common.common-svg-sprite-use(width = 24, height = 24, symbolId = "bi-x-lg", id="header-banner-menu-toggle-on-icon")
    </div>
    @endif

    <div id="header-banner-container">
        <div id="header-banner-logo-and-title-container">
            <div id="header-banner-logo">
                <a href="/${MultipageConstants.SEGMENT_MULTIPAGE}">
                    <img src="${webResourcePathPrefixes.img()}haikudepot-gray.svg" width="32" height="32">
                </a>
            </div>
            <div id="header-banner-title">
                <a href="/${MultipageConstants.SEGMENT_MULTIPAGE}">
                    <span class="title">${internationalizationSupplier.getMessage("mp.banner.name.title")}</span>
                    <span class="title-short">${internationalizationSupplier.getMessage("mp.banner.name.title_short")}</span>
                </a>
            </div>
        </div>
        <div id="header-banner-search-and-auth-container">

            @if (showSearch)
            <div id="header-banner-search">
                <form action="/${MultipageConstants.SEGMENT_MULTIPAGE}/${BannerSearchController.SEGMENT_BANNER_SEARCH}">
                    <input
                            size="12"
                            type="search"
                            name="${MultipageConstants.KEY_SEARCHEXPRESSION}"
                            id="header-search-input"
                            placeholder="${internationalizationSupplier.getMessage("mp.banner.search.placeholder")}">
                </form>
            </div>
            @endif

            @if (showUser)
            <div id="header-banner-user">
                @if (null != userAndNavigation.user())
                    <a href="${userAndNavigation.viewUriComponents().toUriString()}" id="header-banner-user-view-anchor">
                        @template.multipage.common.common-svg-sprite-use(width = 24, height = 24, symbolId = "bi-person-circle")
                    </a>
                @else
                    <a href="${userAndNavigation.loginUriComponents().toUriString()}" id="header-banner-user-login-anchor">
                        @template.multipage.common.common-svg-sprite-use(width = 24, height = 24, symbolId = "bi-person-circle")
                    </a>
                @endif
            </div>
            @endif

        </div>
    </div>

</header>

${content}

@if (null != menuGroups)
@template.multipage.common.common-menu(menuGroups = menuGroups, internationalizationSupplier = internationalizationSupplier)
@endif

</body>
</html>