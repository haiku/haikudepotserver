@import java.util.List
@import org.haiku.haikudepotserver.multipage.controller.PkgChangelogController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.jte.TextHelper

@param PkgChangelogController.PkgChangelogData data
@param InternationalizationSupplier internationalizationSupplier

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.pkg_changlelog.title", data.pkgName()),
menuGroups = data.menuGroups(),
internationalizationSupplier = internationalizationSupplier,
cssLeafnames = List.of("pkg-changelog"),
jsLeafnames = List.<String>of(),
content = @`

    <main id="common-main-content-container">

        <h3>${internationalizationSupplier.getMessage("mp.pkg_changelog.title", data.pkgName())}</h3>

        <div id="pkg-changelog-content-container">
            ${TextHelper.htmlFormattedText(data.changelog())}
        </div>

        <ul>
            <li>
                <a href="${data.viewUriComponents().toUriString()}">
                    ${internationalizationSupplier.getMessage("mp.pkg_changelog.action.view_pkg.title")}
                </a>
            </li>
        </ul>

    </main>

`
)