<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import java.util.List
@import gg.jte.support.ForSupport
@import org.apache.commons.collections4.CollectionUtils
@import org.apache.commons.lang3.StringUtils
@import org.haiku.haikudepotserver.multipage.controller.PkgViewController
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.jte.RatingHelper
@import org.haiku.haikudepotserver.multipage.jte.TextHelper
@import org.haiku.haikudepotserver.multipage.jte.TimestampHelper
@import org.haiku.haikudepotserver.multipage.model.PkgCategory
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes
@import org.springframework.web.util.UriComponentsBuilder

@param org.haiku.haikudepotserver.multipage.controller.PkgViewController.PkgViewData data
@param InternationalizationSupplier internationalizationSupplier
@param WebResourcePathPrefixes webResourcePathPrefixes

@template.multipage.common.common-page-mantle(
title = internationalizationSupplier.getMessage("mp.pkg_view.title", data.pkgName(), data.version().toString()),
menuGroups = data.menuGroups(),
userAndNavigation = data.userAndNavigation(),
internationalizationSupplier = internationalizationSupplier,
webResourcePathPrefixes = webResourcePathPrefixes,
cssLeafnames = List.of("pkg-view"),
jsLeafnames = List.<String>of(),
content = @`
    <main>

        <div id="pkg-view-banner-container">

            <div id="pkg-view-banner-container-icon">
                @template.multipage.common.common-pkg-icon(
                pkgName = data.pkgName(),
                size = 64,
                versionDifferentiator = data.version(),
                webResourcePathPrefixes = webResourcePathPrefixes)
            </div>

            <div id="pkg-view-banner-container-details">
                <h3>
                    @if (StringUtils.isNotBlank(data.localizedText().title()))
                        ${data.localizedText().title()}
                    @else
                        ${data.pkgName()}
                    @endif
                    @if (data.isNativeDesktop())<img src="${webResourcePathPrefixes.img()}native.svg" width="16" height="16">@endif
                </h3>
                <div id="pkg-view-banner-container-copyright">
                    @for (ForSupport<String> copyrightForSupport : ForSupport.of(data.copyrights()))
                        &copy; ${copyrightForSupport.get()}@if (!copyrightForSupport.isLast()),@endif
                    @endfor
                </div>
                <div id="pkg-view-banner-container-summary">${data.localizedText().summary()}
                </div>
            </div>
        </div>

        <div id="pkg-view-infoblocks-container">

            @if (data.userRatingInfo().summary().derivedRatingSampleSize() != 0)
                <div>
                    <h4>${data.userRatingInfo().summary().derivedRatingSampleSize()} <a href="#pkg-view-user-ratings-container">${internationalizationSupplier.getMessage("mp.pkg_view.infoblocks.ratings.title")}</a></h4>
                    <h2>${internationalizationSupplier.formatDecimal(data.userRatingInfo().summary().derivedRating(), 1)}</h2>
                    <div class="rating-stars-container">
                        ${RatingHelper.stars(data.userRatingInfo().summary().derivedRating(), 16)}
                    </div>
                </div>
            @endif

            @if (!data.pkgCategories().isEmpty())
                <div>
                    <h4>${internationalizationSupplier.getMessage("mp.pkg_view.infoblocks.categories.title")}</h4>
                    @for (PkgCategory pkgCategory : data.pkgCategories())
                        <div>${pkgCategory.name()}</div>
                    @endfor
                </div>
            @endif

            <div>
                <h4>${internationalizationSupplier.getMessage("mp.pkg_view.infoblocks.version.title")}</h4>
                <div>${data.version().toString()}</div>
                <div class="common-muted">(${TimestampHelper.formatDate(data.versionCreateTimestamp())})</div>
            </div>

            @if (null != data.payloadLength())
                <div>
                    <h4>${internationalizationSupplier.getMessage("mp.pkg_view.infoblocks.size.title")}</h4>
                    <h2>${internationalizationSupplier.formatDecimal(data.payloadLength().quantity(), 1)}</h2>
                    <div>${data.payloadLength().unit().getSuffix()}</div>
                </div>
            @endif

            <div>
                <h4>${internationalizationSupplier.getMessage("mp.pkg_view.infoblocks.repository.title")}</h4>
                <div><a href="#">${data.repositorySource().repository().name()}</a></div>
                @if (null != data.architecture())
                    <div><code>${data.architecture().code()}</code></div>
                @endif
            </div>

        </div>

        <div id="pkg-view-description-and-screenshots-container">

            <div id="pkg-view-description">
                ${TextHelper.htmlFormattedText(data.localizedText().description())}

                <ul>
                    @for (PkgViewController.Url url : data.urls())
                        <li>
                            <a href="${url.url()}">${internationalizationSupplier.getMessage(url.descriptionKey())}</a>
                        </li>
                    @endfor
                    @if (CollectionUtils.isNotEmpty(data.licenses()))
                        <li>
                            ${internationalizationSupplier.getMessage("mp.pkg_view.licenses.prefix", data.licenses().size())}
                            @for (ForSupport<String> license : ForSupport.of(data.licenses()))
                                <code>${license.get()}</code>@if (!license.isLast()), @endif
                            @endfor
                        </li>
                    @endif
                    @if (StringUtils.isNotBlank(data.hpkrDownloadURL()))
                        <li>
                            <a href="${data.hpkrDownloadURL()}">${internationalizationSupplier.getMessage("mp.pkg_view.hpkg_download.title")}</a>
                        </li>
                    @endif
                    @if (null != data.viewChangelogUriComponents())
                        <li>
                            <a href="${data.viewChangelogUriComponents().toUriString()}">
                                ${internationalizationSupplier.getMessage("mp.pkg_view.action.changelog.title")}
                            </a>
                        </li>
                    @endif

                </ul>
            </div>

            @if (!data.screenshotInfo().screenshots().isEmpty())
                <div id="pkg-view-screenshots">
                    <div id="pkg-view-screenshots-description">${internationalizationSupplier.getMessage("mp.pkg_view.screenshots.description", data.screenshotInfo().screenshots().size())}</div>
                    @if (data.screenshotInfo().hasMoreThanOne())
                        <div id="pkg-view-screenshots-nav">
                            <small>
                                @template.multipage.common.common-pagination(
                                internationalizationSupplier = internationalizationSupplier,
                                uriComponents = data.uriComponents(),
                                offsetQueryParameter = PkgViewController.KEY_SCREENSHOTINDEX,
                                pagination = data.screenshotInfo().screenshotsPagination(),
                                webResourcePathPrefixes = webResourcePathPrefixes
                                )
                            </small>
                        </div>
                    @endif
                    <div id="pkg-view-screenshots-image">
                        <a href="${data.screenshotInfo().currentScreenshot().viewUriComponents().toUriString()}">
                            <img
                                    src="${data.screenshotInfo().currentScreenshot().thumbnailUriComponents().toUriString()}">
                        </a>
                    </div>
                </div>
            @endif

        </div>

        @if (!data.userRatingInfo().userRatings().isEmpty())
            <div id="pkg-view-user-ratings-container">

                <div id="pkg-view-user-ratings-title">
                <h4>${internationalizationSupplier.getMessage("mp.pkg_view.user_ratings.title", data.userRatingInfo().userRatings().size())}</h4>
                </div>

                @if (data.userRatingInfo().pagination().pageCount() > 1)
                <div id="pkg-view-user-ratings-nav">
                <small>
                    @template.multipage.common.common-pagination(
                    internationalizationSupplier = internationalizationSupplier,
                    uriComponents = UriComponentsBuilder.newInstance().uriComponents(data.uriComponents()).fragment("pkg-view-user-ratings-container").build(),
                    offsetQueryParameter = PkgViewController.KEY_USERRATINGOFFSET,
                    pagination = data.userRatingInfo().pagination(),
                    webResourcePathPrefixes = webResourcePathPrefixes
                    )
                </small>
                </div>
                @endif

                <div id="pkg-view-user-ratings">

                    @for (PkgViewController.UserRating userRating : data.userRatingInfo().userRatings())
                        <div class="pkg-view-user-rating-item">
                            <div class="pkg-view-user-rating-rating-and-user">
                                ${RatingHelper.stars(userRating.rating(), 16)} -
                                <code>${userRating.userNickname()}</code>
                            </div>
                            <div class="pkg-view-user-rating-date-and-nav">
                                <small>
                                    ${TimestampHelper.formatDate(userRating.createTimestamp())}
                                    | ${internationalizationSupplier.getMessage("mp.pkg_view.user_ratings.action.more")}
                                </small>
                            </div>
                            <div class="pkg-view-user-rating-comment">
                                <small>${userRating.comment()}</small>
                            </div>
                            @if (userRating.pkgVersionDiffersFromViewed())
                                <div class="pkg-view-user-rating-different-version">
                                    <small>${userRating.pkgVersion().toString()}</small>
                                </div>
                            @endif
                        </div>
                    @endfor

                </div>

            </div>
        @endif

    </main>
`
)