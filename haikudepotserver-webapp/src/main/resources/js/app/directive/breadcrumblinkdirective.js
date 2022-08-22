/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will adjust a hyperlink to which it is associated such that clicking on the hyperlink will
 * take you to the breadcrumb.  The 'breadcrumb factory' is able to produce breadcrumb items.  Here a 'key' is
 * provided which indicates to the 'breadcrumb factory' which items to create.  The name of the
 * methods on that service are of the form "createCompletePasswordReset" so the key that should be provided here
 * is of the form "completePasswordReset".</p>
 *
 * <p>The assumption is that parameters will be provided and if the parameters are not provided or any member of
 * the parameters is false then the link will not work.</p>
 */

angular.module('haikudepotserver').directive(
    'breadcrumbLink',[
        'breadcrumbFactory', 'breadcrumbs',
        function(breadcrumbFactory, breadcrumbs) {
            return {
                restrict: 'A',
                link: function(scope, elem, attributes) {

                    var key = attributes['breadcrumbLink'];
                    var parametersExpr = attributes['breadcrumbLinkParameters'];

                    function createItem() {
                        var parameters = scope.$eval(parametersExpr);

                        if(!key || !parameters) {
                            return undefined;
                        }

                        for(var i = 0; i < parameters.length; i++) {
                            if(!parameters[i]) {
                                return undefined;
                            }
                        }

                        var factoryFn = breadcrumbFactory['create' + key.charAt(0).toUpperCase() + key.substring(1)];
                        return factoryFn.apply(this, parameters);
                    }

                    function refreshHref() {
                        var item = createItem();

                        if(item) {
                            elem.attr('href', breadcrumbFactory.toFullPath(item));
                        }
                        else {
                            elem.attr('href','');
                        }
                    }

                    elem.on('click', function(event) {
                        if(0 == event.button) { // left button only.
                            event.preventDefault();

                            scope.$apply(function () {
                                var item = createItem();

                                if (!item) {
                                    throw Error('it was not possible to create a breadcrumb item');
                                }

                                breadcrumbs.pushAndNavigate(item);
                            });
                        }
                    });

                    scope.$watchCollection(parametersExpr, function() { refreshHref(); });

                }
            };
        }
    ]
);