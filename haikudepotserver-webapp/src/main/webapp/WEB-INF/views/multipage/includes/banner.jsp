<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<div>
    <div id="banner-container">

    <span id="banner-title" class="multipage-banner-title">
        <div>Haiku Depot Server <span><spring:message code="multipage.banner.title.suffix"></spring:message></span></div>
    </span>

        <div id="banner-actions" class="multipage-banner-actions">
            <div id="banner-multipage-note">
                <spring:message code="multipage.banner.note"></spring:message>;
                <a href="/">
                    <spring:message code="multipage.banner.note.full"></spring:message>
                </a>
            </div>
            <div>
                <multipage:naturalLanguageChooser></multipage:naturalLanguageChooser>
            </div>
        </div>

    </div>

</div>