<%@ page session="false" language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%--
This section of HTML is used to display to the user that their browser is not supported.  It is rendered by default
and then, when the AngularJS environment runs, a special directive will hide this div; proving that the AngularJS
environment is working.
--%>

<div id="unsupported">
    <div id="unsupported-container">
        <div class="unsupported-image"><img src="/__img/haikudepot-error.png" alt="Haiku Depot Server"></div>
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
            funktionieren auf alle F&#xE4;lle.  Es steht auch eine 
            <a href="/__multipage">vereinfachte Darstellung</a> 
            zur Verfügung.
        </div>

        <div class="unsupported-message-container">
            T&#xE1;to aplik&#xE1;cia nefunguje spr&#xE1;vne v prehliada&#x10D;i, ktor&#xFD; pr&#xE1;ve pou&#x17E;&#xED;vate.
            M&#xF4;&#x17E;e to by&#x165; bu&#x10F; preto, &#x17E;e ste si v prehliada&#x10D;i vypli JavaScript alebo preto, &#x17E;e v&#xE1;&#x161; prehliada&#x10D; nie je podporovan&#xFD;.
            Je zn&#xE1;me, &#x17E;e funguj&#xFA; najnov&#x161;ie verzie prehliada&#x10D;ov:
            <a href="https://www.haiku-os.org/docs/userguide/en/applications/webpositive.html">WebPositive</a>,
            <a href="https://www.mozilla.org/firefox">Firefox</a>
            a
            <a href="https://www.google.com/chrome/browser/">Google Chrome</a>
            K dispoz&#xED;cii je aj
            <a href="/__multipage">zjednodu&#x161;en&#xE9; pou&#x17E;&#xED;vate&#x13E;sk&#xE9; rozhranie</a>.
        </div>

    </div>
</div>

<supported></supported>
