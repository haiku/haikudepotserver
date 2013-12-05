<%@ page session="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="hds" uri="/WEB-INF/haikudepotserver.tld" %>

<%--
This is a single page application and this is essentially the 'single page'.  It boots-up some libraries and other
web-resources and then this starts the java-script single page environment driven by the AngularJS library.
--%>

<html ng-app="haikudepotserver">

<head>

    <title>Haiku Depot Web</title>

    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <link rel="stylesheet" href="/bootstrap/dist/css/bootstrap.min.css">
    <link rel="icon" type="image/png" href="/img/favicon.png" >

    <hds:webresourcegroup code="libScripts"/>
    <hds:webresourcegroup code="appScripts"/>
    <hds:webresourcegroup code="appStylesheets"/>

</head>

<body>
<banner></banner>
<div class="container">
    <div ng-view></div>
</div>
</body>

</html>