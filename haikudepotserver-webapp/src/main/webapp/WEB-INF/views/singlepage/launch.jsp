<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="jwr" uri="http://jawr.net/tags" %>
<%@ taglib prefix="singlepage" uri="/WEB-INF/singlepage.tld"%>

<%--
This is a single page application and this is essentially the 'single page'.  It boots-up some libraries and other
web-resources and then this starts the java-script single page environment driven by the AngularJS library.
--%>

<html ng-app="haikudepotserver" environment-class="">

<head>

    <title>Haiku Depot Web</title>

    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <%@include file="/WEB-INF/includes/searchheadlink.jsp"%>
    <%@include file="/WEB-INF/includes/favicons.jsp"%>

    <jwr:script src="/bundles/libs.js"></jwr:script>
    <jwr:script src="/bundles/app.js"></jwr:script>
    <jwr:style src="/bundles/app.css"></jwr:style>

    <c:forEach items="${topTemplates}" var="topTemplate">
        <singlepage:embeddedtemplate template="${topTemplate}"></singlepage:embeddedtemplate>
    </c:forEach>

</head>

<%@include file="/WEB-INF/includes/unsupported.jsp"%>

<c:if test="${param['banner']==null || param['banner']=='true'}">
    <banner></banner>
</c:if>
<c:if test="${param['breadcrumbs']==null || param['breadcrumbs']=='true'}">
    <breadcrumbs></breadcrumbs>
</c:if>

<div class="container">
    <div ng-view></div>
</div>

<div class="footer"></div>

<%--
This IFRAME can be used by application logic to cause a download to occur.
--%>

<iframe id="download-iframe" style="display:none"></iframe>

</body>

</html>