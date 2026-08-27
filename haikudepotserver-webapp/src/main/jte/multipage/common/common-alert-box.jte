<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import gg.jte.Content
@import org.haiku.haikudepotserver.multipage.model.AlertType
@import java.util.Locale

@param AlertType type
@param Content content

<div class="common-alert-container common-alert-${type.name().toLowerCase(Locale.ROOT)}-container">
    <div class="common-alert-icon">
        @if (type == AlertType.INFO)
            @template.multipage.common.common-svg-sprite-use(width = 16, height = 16, symbolId = "bi-info-circle-fill")
        @else
            @template.multipage.common.common-svg-sprite-use(width = 16, height = 16, symbolId = "bi-exclamation-circle-fill")
        @endif
    </div>
    <div class="common-alert-content-container">
        ${content}
    </div>
</div>