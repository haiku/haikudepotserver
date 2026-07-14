<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>

<%--
Supplied with a `Map` of parameters, this template will render FORM INPUT elements
representing the key/value pairs.
--%>

@import java.util.Map

@param Map<String, String> parameters

@for (Map.Entry<String, String> entry : parameters.entrySet())
    <input type="hidden" name="${entry.getKey()}" value="${entry.getValue()}">
@endfor

