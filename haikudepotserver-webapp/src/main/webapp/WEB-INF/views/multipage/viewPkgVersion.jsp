<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@include file="/WEB-INF/views/multipage/includes/prelude.jsp"%>

<html>

<head>

    <title>Haiku Depot Server</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <%@include file="/WEB-INF/includes/searchheadlink.jsp"%>
    <%@include file="/WEB-INF/includes/favicons.jsp"%>
    <%@include file="includes/generatedstyle.jsp"%>

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
                <h1><c:out value="${data.resolvedPkgVersionLocalization.summary}"/></h1>
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

        <c:if test="${data.pkgVersion.isLatest && data.pkgVersion.pkgUserRatingAggregate.present}">
            <div class="pkg-rating-indicator-container">
                <multipage:ratingIndicator value="${data.pkgVersion.derivedAggregatedUserRating.get()}"></multipage:ratingIndicator>
              <span class="pkg-ratings-indicator-sample">
                <small>
                    <spring:message
                            code="viewPkg.derivedUserRating.sampleSize"
                            arguments="${data.pkgVersion.derivedAggregatedUserRatingSampleSize.get()}">
                    </spring:message>
                </small>
              </span>
            </div>
        </c:if>

        <div id="pkg-description-container">
            <p>
                <multipage:plainTextContent value="${data.resolvedPkgVersionLocalization.description}"></multipage:plainTextContent>
            </p>
        </div>

        <div id="pkg-metadata-container">

            <dl>
                <dt><spring:message code="viewPkg.name.title"/></dt>
                <dd><c:out value="${data.pkgVersion.pkg.name}"/></dd>
                <dt><spring:message code="viewPkg.repository.title"/></dt>
                <dd><c:out value="${data.pkgVersion.repositorySource.repository.name}"/></dd>
                <dt><spring:message code="viewPkg.repositorySource.title"/></dt>
                <dd><c:out value="${data.pkgVersion.repositorySource.code}"/></dd>
                <dt><spring:message code="viewPkg.version.title"/></dt>
                <dd><multipage:pkgVersionLabel pkgVersion="${data.pkgVersion}"/></dd>
                <c:if test="${not empty data.pkgVersion.payloadLength}">
                    <dt><spring:message code="viewPkg.payloadLength.title"/></dt>
                    <dd>
                        <multipage:dataLength length="${data.pkgVersion.payloadLength}"/>
                        <span class="muted">(<c:out value="${data.pkgVersion.payloadLength}"/> B)</span>
                    </dd>
                </c:if>
                <dt><spring:message code="viewPkg.sourceAvailable.title"></spring:message></dt>
                <dd><spring:message code="gen.${data.isSourceAvailable ? 'yes' : 'no'}"/></dd>
                <dt><spring:message code="viewPkg.categories.title"></spring:message></dt>
                <dd>
                    <c:if test="${empty data.pkgVersion.pkg.pkgSupplement.pkgPkgCategories}">
                        <spring:message code="viewPkg.categories.none"></spring:message>
                    </c:if>
                    <c:if test="${not empty data.pkgVersion.pkg.pkgSupplement.pkgPkgCategories}">
                         <c:forEach
                                 items="${data.pkgVersion.pkg.pkgSupplement.pkgPkgCategories}"
                                 varStatus="pkgPkgCategoryStatus"
                                 var="pkgPkgCategory"><c:if test="${not pkgPkgCategoryStatus.first}">,</c:if>
                             <spring:message code="pkgCategory.${pkgPkgCategory.pkgCategory.code.toLowerCase()}.title"/>
                         </c:forEach>
                    </c:if>
                </dd>
                <dt><spring:message code="viewPkg.versionViews.title"/></dt>
                <dd><c:out value="${data.pkgVersion.viewCounter}"></c:out></dd>
            </dl>

        </div>


    </div>

</div>

<div class="footer"></div>

</body>

</html>
