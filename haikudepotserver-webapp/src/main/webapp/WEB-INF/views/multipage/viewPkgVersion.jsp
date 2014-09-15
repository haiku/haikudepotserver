<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@include file="/WEB-INF/views/multipage/includes/prelude.jsp"%>

<html>

<head>

    <title>Haiku Depot Web</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@include file="/WEB-INF/includes/favicons.jsp"%>

    <%-- will use the same CSS as the main application --%>
    <jwr:style src="/bundles/app.css"></jwr:style>

</head>

<body>
<%@include file="includes/banner.jsp"%>

<div class="container">

    <div id="breadcrumbs-container">
        <ul>
            <li>
                <a href="<c:out value="${data.homeUrl}"></c:out>">
                    <spring:message code="breadcrumb.home.title"></spring:message>
                </a>
            </li>
            <li>
                <span ng-show="isItemActive(item)"><c:out value="${data.pkgVersion.pkg.name}"></c:out></span>
            </li>
        </ul>
    </div>

    <div class="content-container">

        <div id="pkg-title">
            <div id="pkg-title-icon">
                <multipage:pkgIcon pkgVersion="${data.pkgVersion}" size="32"/>
            </div>
            <div id="pkg-title-text">
                <h1><c:out value="${data.pkgVersion.getPkgVersionLocalizationOrFallbackByCode(data.currentNaturalLanguage.code).summary}"/></h1>
                <div class="muted">
                    <small>
                        <c:out value="${data.pkgVersion.pkg.name}"></c:out>
                        -
                        <multipage:pkgVersionLabel pkgVersion="${data.pkgVersion}"></multipage:pkgVersionLabel>
                        -
                        <c:out value="${data.pkgVersion.architecture.code}"></c:out>
                    </small>
                </div>
            </div>
        </div>

        <c:if test="${data.pkgVersion.isLatest && not empty data.pkgVersion.pkg.derivedRating}">
            <div class="pkg-rating-indicator-container">
                <multipage:ratingIndicator value="${data.pkgVersion.pkg.derivedRating}"></multipage:ratingIndicator>
              <span class="pkg-ratings-indicator-sample">
                <small>
                    <spring:message
                            code="viewPkg.derivedUserRating.sampleSize"
                            arguments="${data.pkgVersion.pkg.derivedRatingSampleSize}">
                    </spring:message>
                </small>
              </span>
            </div>
        </c:if>

        <div id="pkg-description-container">
            <p>
                <multipage:plainTextContent value="${data.pkgVersion.getPkgVersionLocalizationOrFallbackByCode(data.currentNaturalLanguage.code).description}"></multipage:plainTextContent>
            </p>
        </div>

    </div>

</div>

<div class="footer"></div>

</body>

</html>