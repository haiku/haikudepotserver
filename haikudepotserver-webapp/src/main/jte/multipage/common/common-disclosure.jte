<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>

<%--
This template will encode some content in a box which can be toggled on and off to
show and hide the content.
--%>

@import gg.jte.Content
@import org.apache.commons.lang3.StringUtils
@import org.haiku.haikudepotserver.multipage.jte.DisclosureHelper

@param boolean shown = false
@param String id = null
@param String shownInputName = null
@param String hiddenTitle
@param Content content

<div
        id="${id}"
        class="${DisclosureHelper.disclosureClass(shown)}"
        data-shown-input-name="${StringUtils.trimToNull(shownInputName)}"
>
    @if (StringUtils.isNotBlank(shownInputName))
    <input type="HIDDEN" name="${shownInputName}" value="${shown}">
    @endif
    @template.multipage.common.common-svg-sprite-use(width = 16, height = 16, symbolId = "bi-caret-down-fill", klass = "common-disclosure-toggle-on")
    @template.multipage.common.common-svg-sprite-use(width = 16, height = 16, symbolId = "bi-caret-right-fill", klass = "common-disclosure-toggle-off")
    <div class="common-disclosure-placeholder">${hiddenTitle}</div>
    <div class="common-disclosure-content">${content}</div>
</div>