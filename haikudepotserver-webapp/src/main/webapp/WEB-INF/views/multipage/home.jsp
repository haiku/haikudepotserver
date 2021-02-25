<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@include file="/WEB-INF/views/multipage/includes/prelude.jsp"%>

<%--
  ~ Copyright 2018, Andrew Lindesay
  ~ Distributed under the terms of the MIT License.
  --%>

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

<body onload="hdsOnBodyLoad()">

<%@include file="includes/banner.jsp"%>

<div class="container">
    <div class="content-container home">

        <form method="get" action="/__multipage">
            <input type="hidden" name="natlangcode" value="${data.naturalLanguage.code}">
            <div id="search-criteria-container">
                <div>
                    <select name="repos">
                        <option value=""
                                <c:if test="${empty data.repository}">
                                    selected="selected"
                                </c:if>
                                >
                            <spring:message code="multipage.home.allrepositories"></spring:message>
                        </option>
                        <c:forEach items="${data.allRepositories}" var="aRepository">
                            <option
                                    <c:if test="${aRepository == data.repository}">
                                        selected="selected"
                                    </c:if>
                                    value="${aRepository.code}">
                                <c:out value="${aRepository.name}"></c:out>
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <div>
                    <select name="arch">
                        <c:forEach items="${data.allArchitectures}" var="anArchitecture">
                            <option
                                    <c:if test="${anArchitecture == data.architecture}">
                                        selected="selected"
                                    </c:if>
                                    value="${anArchitecture.code}">
                                <c:out value="${anArchitecture.code}"></c:out>
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <div>
                    <select id="viewcrttyp" name="viewcrttyp" onchange="hdsHideShowPkgCatContainer()">
                        <c:forEach items="${data.allViewCriteriaTypes}" var="aViewCriteriaType">
                            <option
                                    <c:if test="${aViewCriteriaType == data.viewCriteriaType}">
                                        selected="selected"
                                    </c:if>
                                    value="${aViewCriteriaType.name()}">
                                <spring:message code="${aViewCriteriaType.getTitleKey()}"></spring:message>
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <div id="pkgcat-container">
                    <select name="pkgcat">
                        <c:forEach items="${data.allPkgCategories}" var="aPkgCategory">
                            <option
                                    <c:if test="${aPkgCategory == data.pkgCategory}">
                                        selected="selected"
                                    </c:if>
                                    value="${aPkgCategory.code}">
                                <spring:message code="${aPkgCategory.getTitleKey()}"></spring:message>
                            </option>
                        </c:forEach>
                    </select>
                </div>
                <div>
                    <input
                            type="text"
                            placeholder="zlib"
                            name="srchexpr"
                            value="${data.searchExpression}">

                    <button type="submit">
                        <spring:message code="home.searchButton.title"></spring:message>
                    </button>
                </div>
            </div>
        </form>

        <!-- RESULTS -->

        <div id="search-results-container">

            <c:if test="${empty data.pkgVersions}">
                <div class="info-container">
                    <strong><spring:message code="home.noResults.title"></spring:message>;</strong>
                    <spring:message code="home.noResults.description"></spring:message>
                </div>
            </c:if>

            <c:if test="${not empty data.pkgVersions}">
                <div class="table-general-container">

                    <div class="table-general-pagination-container">
                        <multipage:paginationLinks pagination="${data.pagination}"></multipage:paginationLinks>
                    </div>

                    <div class="muted">
                        <c:out value="${data.pagination.total}"></c:out>
                        <c:choose>
                            <c:when test="${1==data.pagination.total}">
                                <spring:message code="gen.pkg.title"></spring:message>
                            </c:when>
                            <c:otherwise>
                                <spring:message code="gen.pkg.title.plural"></spring:message>
                            </c:otherwise>
                        </c:choose>
                    </div>

                    <table class="table-general">
                        <thead>
                        <th></th>
                        <th><spring:message code="gen.pkg.title"></spring:message></th>
                        <th><spring:message code="home.table.payloadlength.title"></spring:message></th>
                        <th><spring:message code="home.table.rating.title"></spring:message></th>
                        <c:if test="${'MOSTRECENT'==data.viewCriteriaType.name()}">
                            <th>
                                        <span class="muted">
                                            <multipage:timestamp value="${pkgVersion.createTimestamp}"></multipage:timestamp>
                                        </span>
                            </th>
                        </c:if>
                        <c:if test="${empty data.repository}">
                        <th><spring:message code="home.table.repository.title"></spring:message></th>
                        </c:if>
                        <th><spring:message code="home.table.version.title"></spring:message></th>
                        <c:if test="${'MOSTRECENT'==data.viewCriteriaType.name()}">
                            <th><spring:message code="home.table.approximateVersionDate.title"></spring:message></th>
                        </c:if>
                        <c:if test="${'MOSTVIEWED'==data.viewCriteriaType.name()}">
                            <th><spring:message code="home.table.versionViewCounter.title"></spring:message></th>
                        </c:if>
                        </thead>
                        <tbody>
                        <c:forEach items="${data.pkgVersions}" var="pkgVersion">
                            <tr>
                                <td>
                                    <multipage:pkgIcon size="16" pkgVersion="${pkgVersion}"></multipage:pkgIcon>
                                </td>
                                <td>
                                    <multipage:pkgVersionLink pkgVersion="${pkgVersion}">
                                        <multipage:pkgVersionTitle
                                                naturalLanguage="${data.naturalLanguage}"
                                                pkgVersion="${pkgVersion}"/>
                                    </multipage:pkgVersionLink>
                                </td>
                                <td>
                                    <multipage:dataLength length="${pkgVersion.payloadLength}"></multipage:dataLength>
                                </td>
                                <td>
                                    <c:if test="${pkgVersion.derivedAggregatedUserRating.present}">
                                        <multipage:ratingIndicator value="${pkgVersion.derivedAggregatedUserRating.get()}"></multipage:ratingIndicator>
                                    </c:if>
                                </td>
                                <c:if test="${empty data.repository}">
                                    <td class="muted">
                                        <c:out value="${pkgVersion.repositorySource.repository.name}"/>
                                    </td>
                                </c:if>
                                <td>
                                    <multipage:pkgVersionLabel pkgVersion="${pkgVersion}"></multipage:pkgVersionLabel>
                                </td>
                                <c:if test="${'MOSTRECENT'==data.viewCriteriaType.name()}">
                                    <td>
                                        <span class="muted">
                                            <multipage:timestamp value="${pkgVersion.createTimestamp}"></multipage:timestamp>
                                        </span>
                                    </td>
                                </c:if>
                                <c:if test="${'MOSTVIEWED'==data.viewCriteriaType.name()}">
                                    <td>
                                        <span class="muted">
                                            <c:out value="${pkgVersion.viewCounter}"></c:out>
                                        </span>
                                    </td>
                                </c:if>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>

                </div>
            </c:if>
        </div>

    </div>
</div>

<div class="footer"></div>

<script>

    <%--
    Some very basic javascript to hide and show the categories list as the user chooses
    the filtering criteria.  This is a global function, but there is *intentionally*
    very little java-script on the page so this is managable.
    --%>

    function hdsHideShowPkgCatContainer() {
        var select = document.getElementById('viewcrttyp');
        var value = select.options[select.selectedIndex].value;
        var container = document.getElementById('pkgcat-container');

        container.setAttribute(
                'style',
                (value && value == 'CATEGORIES') ? '' : 'display:none;'
        );

    }

    function hdsOnBodyLoad() {
        hdsHideShowPkgCatContainer();
    }

</script>

</body>

</html>
