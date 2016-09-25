<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%--
This section of HTML is used to display to the user that their browser is not supported.  It is rendered by default
and then, when the AngularJS environment runs, a special directive will hide this div; proving that the AngularJS
environment is working.
--%>

<div id="unsupported">
    <div id="unsupported-container">
        <div class="unsupported-image"><img src="/img/haikudepot-error.png" alt="Haiku Depot Server"></div>
        <h1>Haiku Depot Server</h1>

        <div class="unsupported-message-container">
            This application doesn't properly work in your current browser.
            It could be that your browser has JavaScript disabled or it may be because this browser is not supported.
            Latest versions of the browsers
            <a href="https://www.haiku-os.org/docs/userguide/en/applications/webpositive.html">WebPositive</a>,
            <a href="https://www.mozilla.org/firefox">Firefox</a>
            and
            <a href="https://www.google.com/chrome/browser/">Google Chrome</a>
            are known to function.  A simple user interface is also
            <a href="/__multipage">available</a>.
        </div>

        <div class="unsupported-message-container">
            Die Anwendung funktioniert in diesem Browser nicht richtig.
            Es k&#xF6;nnte sein, dass JavaScript deaktiviert ist oder der Browser
            generell nicht unterst&#xFC;tzt wird.
            Die neuesten Versionen von
            <a href="https://www.haiku-os.org/docs/userguide/en/applications/webpositive.html">WebPositive</a>,
            <a href="https://www.mozilla.org/firefox">Firefox</a>
            und <a href="https://www.google.com/chrome/browser/">Google Chrome</a>
            funktionieren auf alle F&#xE4;lle.  Eine
            <a href="/__multipage">einfache Darstellung</a>
            steht auch zur Verfügung.
        </div>

    </div>
</div>

<supported></supported>
