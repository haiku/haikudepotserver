@import java.util.List
@import java.util.Objects
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.Country
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes
@import org.apache.commons.lang3.StringUtils

@param org.haiku.haikudepotserver.multipage.controller.RepositorySourceMirrorAddEditController.RepositorySourceMirrorAddEditData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.repository_source_mirror_add_edit.title"),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.of("repository-source-view"),
jsLeafnames = List.<String>of(),
content = @`

    <main id="common-main-content-container">

        <form method="POST" action="${data.addEditActionUriComponents().toUriString()}" class="common-form">

            <input type="hidden" name="sessionDataCode" value="${data.sessionDataCode()}">
            <input type="hidden" name="code" value="${data.editRepositorySourceMirrorCode()}">

            <div class="common-form-input-container">
                <label>
                    ${internationalizationSupplier.getMessage("mp.repository_source_mirror_add_edit.repository_source_code.label")}
                </label>
                <div><code>${data.repositorySourceCode()}</code></div>
            </div>
            <div class="common-form-input-container">
                <label for="countryCode">
                    ${internationalizationSupplier.getMessage("mp.repository_source_mirror_add_edit.country_code.label")}
                    @template.multipage.common.common-form-required-mark()
                </label>
                <select name="countryCode">
                    @for (Country country : data.countries())
                    <option value="${country.code()}" selected="${Objects.equals(country.code(), data.countryCode())}">
                        ${country.name()}
                    </option>
                    @endfor
                </select>
            </div>
            <div class="common-form-input-container">
                <label for="baseUrl">
                    ${internationalizationSupplier.getMessage("mp.repository_source_mirror_add_edit.base_url.label")}
                    @template.multipage.common.common-form-required-mark()
                </label>
                <input type="text" name="baseUrl" value="${data.baseUrl()}" size="80">
            </div>
            <div class="common-form-input-container">
                <label for="description">
                    ${internationalizationSupplier.getMessage("mp.repository_source_mirror_add_edit.description.label")}
                </label>
                <input type="text" name="description" value="${data.description()}" size="80">
            </div>
            <div class="common-form-submit">
                @if (StringUtils.isNotBlank(data.editRepositorySourceMirrorCode()))
                    <input type="submit" name="submit" value="${internationalizationSupplier.getMessage("mp.repository_source_mirror_add_edit.action.edit.title")}">
                @else
                    <input type="submit" name="submit" value="${internationalizationSupplier.getMessage("mp.repository_source_mirror_add_edit.action.add.title")}">
                @endif
            </div>

        </form>

    </main>

`
)