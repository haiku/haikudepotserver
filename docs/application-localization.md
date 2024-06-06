# Application localization

Application localization refers to the process of adapting the interface of the application to support different languages.  Different languages such as English, German or Chinese are referred to as "natural languages" in the application in order to differentiate from computer languages such as C++, Java or Ruby.  An instance of the application running in a browser has a chosen natural language that has either come about through the user explicitly choosing a language or through the user having authenticated and the authenticated user's configured natural language being employed.

## Dates, times and numerics

In general, presentation of dates, times and numerics is made in a locale-agnostic manner.

## General messages and text

The user interface of the application is implemented in the browser using [AngularJS](http://www.angularjs.org/) version 1.X. This is a single-page architecture.  A service `messageSource` is present within the AngularJS environment that provides a key-to-text mapping system.  Example keys are;

- `gen.home.title`
- `changePassword.action.title`
- `addAuthorizationPkgRule.userNickname.required`

The rest of the application uses the messageSource service, together with these known keys, to localize the text that is used to build the user interface.  A directive, `message` is useful in that it is able to render language-specific text based on the current natural language and a key.

The system does not presently support pluralization.

The mapping from the keys to text is done using java properties files.  These property files are located in the source at;

- `haikudepotserver-webapp/src/main/resources/webmessages_*.properties`
- `haikudepotserver-core/src/main/resources/messages_*.properties`

Variants of the properties file for various natural languages can be found at this location and each file is suffixed by the code for the natural language.

If a key does not have a value specified for a specific natural language then it will fall back to the default of English.

## Formatted HTML / Passive content

Sometimes it is impractical to take plain-text strings and re-format them into complex layouts. In this case, segments of HTML can be used to insert pre-formatted content that is specific to a given natural language. These segments are located at;

```
haikudepotserver-spa1/src/main/javascript/app/passivecontent/*.html
```

This is used, for example, in the "about page". These segments should use a containing element
such as `div` in order to contain the material to be rendered. Files for different languages
will be suffixed with the natural language.

## Emails

Emails are rendered from data models using the [Freemarker](http://freemarker.org/) library.
For each email there is a default which is the English natural language and then there may be variants for various natural languages.  The Freemarker templates for email generation are located at `haikudepotserver-core/src/main/resources/mail/*.ftl`.

## Contributors

The file `.../haikudepotserver-core/src/main/resources/contributors.properties` contains a
list of the people who have contributed to the application (not the data). People who have
contributed to the localization should also include their names in this file.