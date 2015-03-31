/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small piece of text and maybe a hyperlink to show a user.</p>
 */

angular.module('haikudepotserver')
    .factory('passiveContentCache',[
        '$cacheFactory',
        function($cacheFactory) {
            return $cacheFactory('passiveContent',{ capacity:10 });
        }
    ])
    .directive('passiveContent',[
        'passiveContentCache','$http','$log','$q',
        'userState','errorHandling',
        function(
            passiveContentCache,$http,$log,$q,
            userState,errorHandling) {
            return {
                restrict: 'A',
                link : function($scope,element,attributes) {

                    var path = attributes['passiveContent'];

                    function getTemplate(p,naturalLanguageCode) {

                        if(!p || !p.length || !p.substring(p.length-5, p.length) == '.html') {
                            throw Error('the path must be supplied to obtain the passive content and must end with ".html"');
                        }

                        var deferred = $q.defer();
                        var isFallback = !naturalLanguageCode || !naturalLanguageCode.length || 'en' == naturalLanguageCode;
                        var pDash = p;

                        if(!isFallback) {
                            pDash = p.substring(0, p.length-5) + '_' + naturalLanguageCode + '.html';
                        }

                        var cachedResult = passiveContentCache.get(pDash);

                        if(cachedResult) {
                            deferred.resolve(cachedResult);
                        }
                        else {
                            $http.get('/js/app/passivecontent/' + pDash)
                                .success(function (data, status) {

                                    if(200==status) {
                                        passiveContentCache.put(pDash,data);
                                        deferred.resolve(data);
                                    }
                                    else {
                                        $log.error('http status ' + status + ' error has arisen obtaining the passive content; ' + p);
                                        deferred.reject();
                                    }

                                })
                                .error(function(data,status) {

                                    if (404 == status) {
                                        if (!isFallback) {
                                            getTemplate(p).then(
                                                function (fallbackData) {
                                                    deferred.resolve(fallbackData);
                                                },
                                                function () {
                                                    deferred.reject();
                                                }
                                            );
                                        }
                                        else {
                                            $log.error('unable to obtain the passive content; ' + p + ' - not found');
                                            deferred.reject();
                                        }
                                    }
                                    else {
                                        $log.error('unable to obtain the passive content; ' + p + '(status:' + status + ')');
                                        deferred.reject();
                                    }

                                });
                        }

                        return deferred.promise;
                    }

                    function update() {

                        var p = attributes['passiveContent'];

                        if (p && p.length) {

                            getTemplate(p, userState.naturalLanguageCode()).then(
                                function (templateData) {

                                    // now take this to be some html and insert it.
                                    // TODO; render as underscore template? maybe not necessary.
                                    var templateDataEl = angular.element(templateData);
                                    element.children().remove();
                                    element.append(templateDataEl);

                                },
                                function () {
                                    errorHandling.navigateToError(); // already logged.
                                }
                            )

                        }
                    }

                    // EVENT AND CHANGE HANDLING

                    attributes.$observe('passiveContent', function() { update(); } );
                    $scope.$on(
                        'naturalLanguageChange',
                        function(event, newValue, oldValue) {
                            if(!!oldValue) {
                                update();
                            }
                        }
                    );

                }
            }

        }
    ]);