<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>

<%-- this is inline because the localization has to be substituted in --%>

<style>

    #banner-title > div:before {
        content: 'Haiku Depot Server ';
    }

    #banner-multipage-note:before {
        content: '<spring:message code="multipage.banner.note"></spring:message>;';
    }

    @media (max-width:825px) {

        #banner-title > div:before {
            content: 'HDS ';
        }

        #banner-multipage-note:before {
            content: '';
        }

    }

</style>

