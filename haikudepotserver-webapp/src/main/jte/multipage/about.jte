@import java.util.List
@import org.haiku.haikudepotserver.multipage.controller.AboutController
@import org.haiku.haikudepotserver.multipage.controller.AboutController.Contributor
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param org.haiku.haikudepotserver.multipage.controller.AboutController.AboutData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.about.title"),
menuGroups = data.menuGroups(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.<String>of(),
jsLeafnames = List.<String>of(),
content = @`

    <main id="common-main-content-container">

        ${internationalizationSupplier.getPassiveContext("aboutcontent.html")}

        <h3>${internationalizationSupplier.getMessage("mp.about.information.title")}</h3>

        <ul>
            <li id="about-project-version">${internationalizationSupplier.getMessage("mp.about.information.project_version", data.runtimeInformation().projectVersion())}</li>
        </ul>

        <h3>${internationalizationSupplier.getMessage("mp.about.contributors.title")}</h3>

        <table class="common-table" id="about-contributors-table">
            <thead>
            <tr>
                <th>${internationalizationSupplier.getMessage("mp.about.contributors.contribution.title")}</th>
                <th>${internationalizationSupplier.getMessage("mp.about.contributors.name.title")}</th>
            </tr>
            </thead>
            <tbody>
            @for(Contributor contributor : data.contributors())
                <tr>
                    <td>
                        ${internationalizationSupplier.getMessage(contributor.type().getTitleKey())}
                        @if (contributor.type() == AboutController.ContributorType.LOCALIZATION)
                            &mdash; ${contributor.languageTitle()}
                        @endif
                    </td>
                    <td>${contributor.name()}</td>
                </tr>
            @endfor
            </tbody>
        </table>

    </main>

`
)