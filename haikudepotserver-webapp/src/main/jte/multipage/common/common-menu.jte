<%--
Copyright 2026, Andrew Lindesay
Distributed under the terms of the MIT License.
--%>
@import org.apache.commons.lang3.StringUtils
@import org.haiku.haikudepotserver.multipage.model.MenuGroup
@import org.haiku.haikudepotserver.multipage.model.MenuItem
@import java.util.Collection
@import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier

@param Collection<MenuGroup> menuGroups
@param InternationalizationSupplier internationalizationSupplier

<section id="menu-container">

    <div id="menu-groups-container">

    @for (MenuGroup group : menuGroups)

        <div class="menu-group-item">
            @if (StringUtils.isNotBlank(group.titleKey()))
            <h3>${internationalizationSupplier.getMessage(group.titleKey())}</h3>
            @endif

            <ul>
                @for (MenuItem item : group.items())
                    <li><a href="${item.uriComponentsBuilder().toUriString()}">${internationalizationSupplier.getMessage(item.titleKey())}</a></li>
                @endfor
            </ul>
        </div>

    @endfor

    </div>
</section>