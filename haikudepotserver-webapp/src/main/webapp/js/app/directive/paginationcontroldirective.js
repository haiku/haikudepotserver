/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to render a series of page numbers given some information about the pagination.</p>
 */

angular.module('haikudepotserver').directive('paginationControl',[
    '$parse','constants',
    function($parse,constants) {
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

                    if(0 == result % 2) {
                        throw Error('a link count of ' + result + ' is not possible for the pagination control - it must be an odd number');
                    }

                    return result;
                }

                var totalExpression = attributes['total'];
                var offsetExpression = attributes['offset'];
                var maxExpression = attributes['max'];
                var pageControlEs = [];

                function adjustOffset(multiplier) {
                    var offset = $scope.$eval(offsetExpression);
                    var max = $scope.$eval(maxExpression);
                    var total = $scope.$eval(totalExpression);
                    offset += multiplier * max;

                    if(offset >= 0 && offset < total) {
                        $parse(offsetExpression).assign($scope,offset);
                    }
                }

                // so we need this many elements to use as page numbers.

                var topLevelE = angular.element('<ul class="pagination-control-container"></ul>');
                var leftArrowAnchorE = angular.element('<a href="" class="pagination-control-left">'+constants.SVG_LEFT_ARROW+'</a>');
                var rightArrowAnchorE = angular.element('<a href="" class="pagination-control-right">'+constants.SVG_RIGHT_ARROW+'</a>');

                element.replaceWith(topLevelE);

                var leftArrowListItemE = angular.element('<li></li>');
                leftArrowListItemE.append(leftArrowAnchorE);
                topLevelE.append(leftArrowListItemE);

                leftArrowAnchorE.on('click', function(event) {
                    $scope.$apply(function() { adjustOffset(-1); });
                    event.preventDefault();
                    return false;
                });

                for(var i=0;i<deriveLinkCount();i++) {
                    var listItemE = angular.element('<li class=\"app-hide\"></li>');
                    var pageControlE = angular.element('<a href=\"\"></a>');
                    pageControlEs.push(pageControlE);
                    listItemE.append(pageControlE);
                    topLevelE.append(listItemE);

                    pageControlE.on(
                        'click',
                        function(event) {
                            var newOffsetStr = event.target.getAttribute('pagination-offset');

                            if(!newOffsetStr || !newOffsetStr.length) {
                                throw Error('unable to get the selected offset for the target of the pagination event');
                            }

                            $scope.$apply(function() {
                                var existingOffset = $scope.$eval(offsetExpression);
                                var newOffset = parseInt(''+newOffsetStr,10);

                                if(existingOffset != newOffset) {
                                    $parse(offsetExpression).assign($scope, parseInt('' + newOffsetStr, 10));
                                }
                            });

                            event.preventDefault();
                            return false;
                        });
                }

                var rightArrowListItemE = angular.element('<li></li>');
                rightArrowListItemE.append(rightArrowAnchorE);
                topLevelE.append(rightArrowListItemE);

                rightArrowAnchorE.on('click', function(event) {
                    $scope.$apply(function() { adjustOffset(1); });
                    event.preventDefault();
                    return false;
                });

                function disableAllPageControls() {
                    for(var i=0;i<pageControlEs.length;i++) {
                        pageControlEs[i].html('');
                        pageControlEs[i].parent().addClass('app-hide');
                        pageControlEs[i].attr('pagination-offset','');
                    }
                }

                function getOffsetFromPageControlEAtIndex(i) {
                    var a = pageControlEs[i].attr('pagination-offset');

                    if(a && a.length) {
                        return parseInt(''+a,10);
                    }

                    return -1;
                }

                function refreshPageControlsWithValues(total,offset,max) {

                    if(max <= 0) {
                        throw Error('the \'max\' value must be a positive integer');
                    }

                    if(offset < 0) {
                        throw Error('the \'offset\' must be >= 0');
                    }

                    if(offset >= total) {
                        throw Error('the \'offset\' must be < '+total);
                    }

                    var pages = Math.floor((total / max) + (total % max ? 1 : 0));
                    var page = Math.floor(offset / max); // current page.

                    // if we're on the first or the last pages when we will need to add a class to the pagination
                    // controls so that we can control the appearance of the controls.

                    if(0==page) {
                        topLevelE.addClass('pagination-control-on-first');
                    }
                    else {
                        topLevelE.removeClass('pagination-control-on-first');
                    }

                    if(page==pages-1) {
                        topLevelE.addClass('pagination-control-on-last');
                    }
                    else {
                        topLevelE.removeClass('pagination-control-on-last');
                    }

                    function setPageControl(i,pageNumber) {
                        if(null==pageNumber) {
                            pageControlEs[i].text('');
                            pageControlEs[i].parent().addClass('app-hide');
                            pageControlEs[i].removeClass('pagination-control-currentpage');
                            pageControlEs[i].attr('pagination-offset','');
                        }
                        else {
                            pageControlEs[i].text('' + (pageNumber + 1));
                            pageControlEs[i].parent().removeClass('app-hide');
                            pageControlEs[i].attr('pagination-offset',''+pageNumber * max);

                            if(pageNumber==page) {
                                pageControlEs[i].addClass('pagination-control-currentpage');
                            }
                            else {
                                pageControlEs[i].removeClass('pagination-control-currentpage');
                            }
                        }
                    }

                    function linearFillPageControl(startI,length,pageStart) {
                        for(var i=0;i<length;i++) {
                            setPageControl(startI+i,pageStart+i);
                        }
                    }

                    if(pages <= 1) {
                        disableAllPageControls();
                    }
                    else {

                        // this function with p=[0,1] should give a nice curve that passes through 0 when p=0
                        // and passes through 1 when p=1

                        function ramp(p) {
                            return p*p;
                        }

                        function fanFillRightPageControl(pageControlEsStartI) {

                            var pageControlEsFillLength = (pageControlEs.length - pageControlEsStartI);

                            for(var i=0;i<pageControlEsFillLength;i++) {
                                var p = i/(pageControlEsFillLength-1);
                                var f = ramp(p);
                                var maxPagesRightOfPage = (pages - (page + 1))-1;
                                var nextPage = Math.floor((page + 1) + (maxPagesRightOfPage * f));
                                var lastPage = Math.floor(getOffsetFromPageControlEAtIndex(pageControlEsStartI+i-1) / max);
                                nextPage = _.max([nextPage,lastPage+1]);
                                setPageControl(pageControlEsStartI + i,nextPage);
                            }
                        }

                        function fanFillLeftPageControl(pageControlEsStartI) {

                            var pageControlEsFillLength = pageControlEsStartI + 1;

                            for(var i=0;i<pageControlEsFillLength;i++) {
                                var p = i/(pageControlEsFillLength-1);
                                var f = ramp(p);
                                var nextPage = Math.floor((page - 1) - ((page - 1) * f));
                                var lastPage = Math.floor(getOffsetFromPageControlEAtIndex(pageControlEsStartI-i+1) / max);
                                nextPage = _.min([nextPage,lastPage-1]);
                                setPageControl(pageControlEsStartI - i,nextPage);
                            }
                        }

                        // if there are <= pages than controls then just show the pages linearly and hide the rest of
                        // the controls that are not necessary.

                        if(pages <= pageControlEs.length) {
                            linearFillPageControl(0,pages,0);

                            for(var i=pages;i<pageControlEs.length;i++) {
                                setPageControl(i,null);
                            }
                        }
                        else {

                            // we have more pages out there than we have page controls so, we need to put the first
                            // and last page into place, choose a sensible location for the current page and arrange
                            // other sensible options for other pages.

                            var middleI = Math.floor(pageControlEs.length / 2);

                            if(page < middleI) { // close to the left side
                                linearFillPageControl(0,page+1,0);
                                fanFillRightPageControl(page+1);
                            }
                            else {
                                var remainder = pages-page;

                                if(remainder < middleI) { // close to the right side.
                                    linearFillPageControl(pageControlEs.length - remainder,remainder,page);
                                    fanFillLeftPageControl((pageControlEs.length - remainder) - 1);
                                }
                                else {
                                    setPageControl(middleI,page);
                                    fanFillRightPageControl(middleI+1);
                                    fanFillLeftPageControl(middleI-1);
                                }
                            }
                        }
                    }
                }

                // looks at the settings bound to this and will show/hide the page numbers.

                function refreshPageControls() {

                    if(totalExpression && offsetExpression && maxExpression) {

                        var total = $scope.$eval(totalExpression);
                        var offset = $scope.$eval(offsetExpression);
                        var max = $scope.$eval(maxExpression);

                        if(!angular.isUndefined(total) &&
                            !angular.isUndefined(offset) &&
                            !angular.isUndefined(max) &&
                            total > 0) {
                            refreshPageControlsWithValues(total,offset,max);
                        }
                        else {
                            disableAllPageControls();
                        }
                    }
                    else {
                        disableAllPageControls();
                    }
                }

                refreshPageControls();

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

            }
        }
    }
]);