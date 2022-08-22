/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to render a series of page numbers given some information about the pagination.</p>
 */

angular.module('haikudepotserver').directive('paginationControl',[
    '$parse','$location',
    function($parse,$location) {
        return {
            restrict: 'E',
            link : function($scope,element,attributes) {

                var LINK_COUNT_DEFAULT = 5;

                // this is the number of links to show.

                function deriveLinkCount() {
                    var result = attributes['linkCount'];

                    if(angular.isUndefined(result) || null==result) {
                        result = LINK_COUNT_DEFAULT;
                    }
                    else {

                        if (!angular.isNumber(result)) {
                            result = parseInt(''+result,10);
                        }
                    }

                    if(result < 3) {
                        throw Error('a link count of ' + result + ' is not possible for the pagination control - it must be >= 3');
                    }

                    return result;
                }

                var totalExpression = attributes['total'];
                var offsetExpression = attributes['offset'];
                var maxExpression = attributes['max'];
                var queryOffsetKey = attributes['queryOffsetKey'];
                var pageControlEs = [];

                /**
                 * <p>This will return an object containing the parameters of the pagination.</p>
                 */

                function parameters() {

                    var offset = $scope.$eval(offsetExpression);
                    var max = $scope.$eval(maxExpression);
                    var total = $scope.$eval(totalExpression);

                    // it is possible that those may evaluate to strings; in which case it is necessary to
                    // parse those into numerical values.

                    if(!angular.isNumber(offset)) {
                        offset = parseInt(''+offset,10);
                    }

                    if(!angular.isNumber(max)) {
                        max = parseInt(''+max,10);
                    }

                    if(!angular.isNumber(total)) {
                        total = parseInt(''+total,10);
                    }

                    if(max <= 0) {
                        throw Error('the \'max\' value must be a positive integer');
                    }

                    if(offset < 0) {
                        throw Error('the \'offset\' must be >= 0');
                    }

                    if(0 != total && offset >= total) {
                        throw Error('the \'offset\' must be < '+total);
                    }

                    var pages = Math.floor((total / max) + (total % max ? 1 : 0));
                    var page = Math.floor(offset / max); // current page.

                    return {
                        offset : offset,
                        max : max,
                        total : total,
                        pages : pages,
                        page : page
                    };

                }

                // ---------------------
                // NAVIGATION / ALGORITHMS

                /**
                 * <p>This is used to go back or forward a page.</p>
                 * @param direction -1 go back 1 page, 1 go forward one page.
                 */

                function pageJumpBackOrForward(direction) {
                    var p = parameters();
                    var offset = p.offset + (direction * p.max);

                    if(offset >= 0 && offset < p.total) {
                        $parse(offsetExpression).assign($scope, offset);
                    }
                }

                /**
                 * <p>This generates the set of offsets to pages that are suggested for the pagination.</p>
                 */

                function generateSuggestedPages(params, count) {

                    switch(params.pages) {

                        case 0:
                            return [];

                        case 1:
                            return [0];

                        default:
                            var result = [];

                            if (params.pages <= count) {

                                // linear fill

                                for (var i = 0; i < params.pages; i++) {
                                    result.push(i);
                                }
                            }
                            else {

                                // fill the result with rubbish so that it is easy to detect problems.

                                for (var j = 0; j < count; j++) {
                                    result.push(-10);
                                }

                                function fanFillRight(startI) {

                                    var pages = params.pages - 1;
                                    var page = params.page + 1;
                                    var len = count - startI;

                                    for (var k = 0; k < len; k++) {
                                        var p = k / (len - 1);
                                        var f = p * p;
                                        result[startI + k] = Math.max(
                                                result[(startI + k) - 1] + 1,
                                                page + Math.floor(f * (pages - page)));
                                    }

                                }

                                function fanFillLeft(startI) {

                                    var page = params.page - 1; // assume the actual page has been set already

                                    for (var l = 0; l <= startI; l++) {
                                        var p = l / startI;
                                        var f = p * p;
                                        result[startI - l] = Math.min(
                                                result[(startI - l) + 1] - 1,
                                                page - Math.floor(f * page));
                                    }
                                }

                                var middleI = Math.floor(count / 2);

                                if (params.page < middleI) {

                                    for (var m = 0; m <= params.page; m++) {
                                        result[m] = m;
                                    }

                                    fanFillRight(params.page + 1);

                                }
                                else {

                                    var remainder = params.pages - params.page;

                                    if (remainder <= (result.length - middleI) - 1) {

                                        for (var n = 0; n < remainder; n++) {
                                            result[result.length - (n + 1)] = (params.pages - 1) - n;
                                        }

                                        fanFillLeft(result.length - (remainder + 1));

                                    }
                                    else {
                                        result[middleI] = params.page;
                                        fanFillRight(middleI + 1);
                                        fanFillLeft(middleI - 1);
                                    }

                                }
                            }

                            return result;
                    }
                }

                // ---------------------
                // DOM SETUP

                // so we need this many elements to use as page numbers.

                var topLevelE = angular.element('<ul class="pagination-control-container"></ul>');
                var leftArrowImgE = angular.element('<img src="/__img/paginationleft.svg">');
                var rightArrowImgE = angular.element('<img src="/__img/paginationright.svg">');
                var leftArrowAnchorE = angular.element('<a href="" class="pagination-control-left"></a>');
                var rightArrowAnchorE = angular.element('<a href="" class="pagination-control-right"></a>');

                leftArrowAnchorE.append(leftArrowImgE);
                rightArrowAnchorE.append(rightArrowImgE);

                if(!Modernizr.svg) {
                    leftArrowImgE.attr('src','/__img/paginationleft.png');
                    rightArrowImgE.attr('src','/__img/paginationright.png');
                }

                element.replaceWith(topLevelE);

                var leftArrowListItemE = angular.element('<li></li>');
                leftArrowListItemE.append(leftArrowAnchorE);
                topLevelE.append(leftArrowListItemE);

                leftArrowAnchorE.on('click', function(event) {
                    $scope.$apply(function() { pageJumpBackOrForward(-1); });
                    event.preventDefault();
                    return false;
                });

                for(var i=0;i<deriveLinkCount();i++) {
                    var listItemE = angular.element('<li class="app-hide"></li>');
                    var pageControlE = angular.element('<a href=""></a>');
                    pageControlEs.push(pageControlE);
                    listItemE.append(pageControlE);
                    topLevelE.append(listItemE);

                    pageControlE.on(
                        'click',
                        function(event) {
                            if(0 == event.button) { // left button only.

                                event.preventDefault();
                                var newOffsetStr = event.target.getAttribute('pagination-offset');

                                if (!newOffsetStr || !newOffsetStr.length) {
                                    throw Error('unable to get the selected offset for the target of the pagination event');
                                }

                                $scope.$apply(function () {
                                    var existingOffset = $scope.$eval(offsetExpression);
                                    var newOffset = parseInt('' + newOffsetStr, 10);

                                    if (existingOffset != newOffset) {
                                        $parse(offsetExpression).assign($scope, parseInt('' + newOffsetStr, 10));
                                    }
                                });

                            }
                        });
                }

                var rightArrowListItemE = angular.element('<li></li>');
                rightArrowListItemE.append(rightArrowAnchorE);
                topLevelE.append(rightArrowListItemE);

                rightArrowAnchorE.on('click', function(event) {
                    $scope.$apply(function() { pageJumpBackOrForward(1); });
                    event.preventDefault();
                    return false;
                });

                // ---------------------
                // REFRESH DATA

                function refreshPageControls() {
                    var params = parameters();

                    // if we're on the first or the last pages when we will need to add a class to the pagination
                    // controls so that we can control the appearance of the controls.

                    if(0==params.total || 0==params.page) {
                        topLevelE.addClass('pagination-control-on-first');
                    }
                    else {
                        topLevelE.removeClass('pagination-control-on-first');
                    }

                    if(0==params.total || params.page==params.pages-1) {
                        topLevelE.addClass('pagination-control-on-last');
                    }
                    else {
                        topLevelE.removeClass('pagination-control-on-last');
                    }

                    // now render the pages in between.

                    var suggestedPages = generateSuggestedPages(params, pageControlEs.length);

                    for(var i=0;i<pageControlEs.length;i++) {

                        if(i >= suggestedPages.length) {
                            pageControlEs[i].parent().addClass('app-hide');
                            pageControlEs[i].removeClass('pagination-control-currentpage');
                            pageControlEs[i].attr('pagination-offset','');
                        }
                        else {
                            var offsetI = (suggestedPages[i] * params.max);

                            pageControlEs[i].parent().removeClass('app-hide');

                            if(params.page == suggestedPages[i]) {
                                pageControlEs[i].addClass('pagination-control-currentpage');
                            }
                            else {
                                pageControlEs[i].removeClass('pagination-control-currentpage');
                            }

                            pageControlEs[i].attr('pagination-offset',''+offsetI);
                            pageControlEs[i].text('' + (suggestedPages[i] + 1));

                            // it may be possible to add an href to the anchors so that
                            // one can right-click the page links to get a new tab showing
                            // that page.

                            if(queryOffsetKey && queryOffsetKey.length) {
                                var searchI = _.clone($location.search());
                                searchI[queryOffsetKey] = '' + offsetI;

                                pageControlEs[i].attr('href',_.reduce(
                                    _.keys(searchI),
                                    function(memo, key) {
                                        if('bcguid'==key) {
                                            return memo;
                                        }
                                        else {
                                            return memo +
                                                ((-1 == memo.indexOf('?')) ? '?' : '&') +
                                                encodeURI(key) + '=' + encodeURI(searchI[key]);
                                        }
                                    },
                                    window.location.pathname + '#' + $location.path()
                                ));

                            }
                        }

                    }

                }

                // ---------------------
                // EVENT OBSERVATION

                // This event has to be caught so that the links on the pagination show the correct
                // search items; otherwise they will be initially setup, but if the user chooses to
                // filter by some other settings (category, search term etc..) then the hyperlink
                // on the pagination will be wrong.

                if(queryOffsetKey) {
                    $scope.$on('$routeUpdate', function() {
                        refreshPageControls();
                    });
                }

                if(totalExpression) {
                    $scope.$watch(totalExpression, function () {
                        refreshPageControls();
                    });
                }

                if(offsetExpression) {
                    $scope.$watch(offsetExpression, function () {
                        refreshPageControls();
                    });
                }

                if(maxExpression) {
                    $scope.$watch(maxExpression, function () {
                        refreshPageControls();
                    });
                }

                refreshPageControls();

            }
        }
    }
]);