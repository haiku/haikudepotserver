/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a link that leads the user to information about
 * how data is used in the system.</p>
 */


angular.module('haikudepotserver').directive('userUsageConditionsLink',
    [
        // no injections required
        function () {
            return {
                restrict: 'E',
                transclude: true,
                link: function ($scope, element, attributes, ctrl, transclude) {
                    var codeExpression = attributes['code'];

                    function generateUrl(code) {
                        return '/__user/usageconditions/' +
                            ((!code || !code.length) ? 'latest' : code) +
                            '/document.html'; // '.md' also possible
                    }

                    function updateUrlInHyperlinkEl(code) {
                        hyperlinkEl.attr('href', generateUrl(code));
                    }

                    var hyperlinkEl = angular.element('<a target="_blank" href=""></a>');
                    updateUrlInHyperlinkEl();
                    element.replaceWith(hyperlinkEl);

                    // takes the material provided inside the directive element
                    // and renders it inside the generated element.

                    transclude(
                        $scope,
                        function (clone) {
                            hyperlinkEl.append(clone);
                        }
                    );

                    $scope.$watch(codeExpression, function (code) {
                        updateUrlInHyperlinkEl(code);
                    });
                }
            };
        }
    ]
);