<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>

<%--
Creates a link that is enclosed within a form. The form will contain an action and then
when the user clicks on the link, the form is submitted. This makes it possible to have
a hyperlink do a POST request to an endpoint to perform some action which is mutable.
--%>

@import org.springframework.web.util.UriComponents
@import org.apache.commons.lang3.StringUtils
@import gg.jte.Content

@param String formName = null
@param String actionCode
@param UriComponents uriComponents
@param Content content

!{String actualFormName = StringUtils.isEmpty(formName) ? "oneshot_form_%s".formatted(actionCode.toLowerCase()) : formName;}
<form name="${actualFormName}" method="POST" action="${uriComponents.toUriString()}">
    <input type="hidden" name="action" value="${actionCode}">
    <a href="#" onclick="document.forms['${actualFormName}'].submit(); return false;">${content}</a>
</form>
