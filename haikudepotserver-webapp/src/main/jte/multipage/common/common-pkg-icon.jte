@import org.haiku.haikudepotserver.multipage.jte.PkgResourceHelper
@import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes

@param String pkgName
@param Object versionDifferentiator = null
@param int size = 32
@param boolean isNativeDesktop = false
@param WebResourcePathPrefixes webResourcePathPrefixes

<div class="common-pkg-icon-container">
    <img src="${PkgResourceHelper.pkgIconUrl(pkgName, size * 2, versionDifferentiator)}" width="${size}" height="${size}">
    @if (isNativeDesktop)
    <div class="common-pkg-icon-badge">
        <img src="${webResourcePathPrefixes.img()}native.svg" width="${size / 3}" height="${size / 3}">
    </div>
    @endif
</div>