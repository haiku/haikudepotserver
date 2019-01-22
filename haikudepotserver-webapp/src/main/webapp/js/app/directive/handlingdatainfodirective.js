/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a link that leads the user to information about
 * how data is used in the system.</p>
 */


angular.module('haikudepotserver').directive('handlingDataInfo',
    [
        '$q', 'runtimeInformation', 'messageSource', 'userState',
        function ($q, runtimeInformation, messageSource, userState) {
            return {
                restrict: 'E',
                link: function ($scope, element, attributes) {

                    console.log('*U*');
                    var hyperlinkEl = angular.element('<a target="_blank" href="#">...</a>');
                    element.replaceWith(hyperlinkEl);

                    function updateValue() {
                        function setupElement(hyperlink, text) {
                            hyperlinkEl.attr('href', hyperlink);
                            hyperlinkEl.text(text);
                        }

                        $q.all([
                            runtimeInformation.getRuntimeInformation(),
                            messageSource.get(
                                userState.naturalLanguageCode(),
                                'dataHandlingInfo.link.title')
                        ]).then(
                            function (result) {
                                setupElement(result[0].dataHandlingInformationUrl, result[1]);
                            }
                        )
                    }

                    updateValue();

                    $scope.$on(
                        "naturalLanguageChange",
                        function (event, newValue, oldValue) {
                            if (!!oldValue) {
                                updateValue();
                            }
                        }
                    );

                }
            };
        }
    ]
);