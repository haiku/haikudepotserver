<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>

@param int width
@param int height
@param String symbolId
@param String id = ""
@param String klass = ""

<svg id="${id}" width="${width}" height="${width}" class="${klass}"><use xlink:href="#${symbolId}"></use></svg>