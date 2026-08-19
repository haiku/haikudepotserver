@import org.haiku.haikudepotserver.multipage.jte.PaginationHelper
@import org.haiku.haikudepotserver.multipage.model.Page
@import gg.jte.support.ForSupport
@import java.util.List
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier
@import org.haiku.haikudepotserver.multipage.model.Pagination
@import org.springframework.web.util.UriComponents

@param UriComponents uriComponents
@param String offsetQueryParameter
@param InternationalizationSupplier internationalizationSupplier
@param Pagination pagination

<div class="common-pagination">

    @if (null != pagination)

    <div>
        @if(pagination.hasPreviousPage())
            <a href="${PaginationHelper.urlAtPage(uriComponents, offsetQueryParameter, pagination.previousPage())}">@template.multipage.common.common-svg-sprite-use(width = 16, height = 16, symbolId = "bi-arrow-left-short")</a>
        @else
            @template.multipage.common.common-svg-sprite-use(
                width = 16, height = 16,
                klass = "common-muted",
                symbolId = "bi-arrow-left-short")
        @endif
    </div>

    !{List<Page> pages = pagination.generateSuggestedPages(5);}

    @for (ForSupport<Page> forPage : ForSupport.of(pages))
        @if(forPage.get().isCurrent())
            <div class="common-pagination-selected">${forPage.get().pageNumber() + 1}</div>
        @else
            <div><a href="${PaginationHelper.urlAtPage(uriComponents, offsetQueryParameter, forPage.get())}">${forPage.get().pageNumber() + 1}</a></div>
        @endif

        <%-- This will put an ellipses between the page numbers if they are not sequential --%>
        @if (!forPage.isLast() && (forPage.get().pageNumber() + 1) != pages.get(forPage.getIndex() + 1).pageNumber())
            &hellip;
        @endif

    @endfor

    <div>
        @if(pagination.hasNextPage())
            <a href="${PaginationHelper.urlAtPage(uriComponents, offsetQueryParameter, pagination.nextPage())}">@template.multipage.common.common-svg-sprite-use(width = 16, height = 16, symbolId = "bi-arrow-right-short")</a>
        @else
            @template.multipage.common.common-svg-sprite-use(
                width = 16, height = 16,
                klass = "common-muted",
                symbolId = "bi-arrow-right-short")
        @endif
    </div>

    @endif

</div>