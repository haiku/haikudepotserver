<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>

<%--
Renders a package icon.
--%>

@import org.haiku.haikudepotserver.multipage.jte.PkgResourceHelper

@param String pkgName
@param Object versionDifferentiator = null
@param int size = 32
@param boolean isNativeDesktop = false

<div class="common-pkg-icon-container">
    <img src="${PkgResourceHelper.pkgIconUrl(pkgName, size * 2, versionDifferentiator)}" width="${size}" height="${size}">
    @if (isNativeDesktop)
    <div class="common-pkg-icon-badge">
        <img src="/__img/native.svg" width="${size / 3}" height="${size / 3}">
    </div>
    @endif
</div>