/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive takes a job object with various fields such as the start/finish
 * timestamp and the like and displays a little piece of text that outlines how the
 * job is going; finished, started etc...</p>
 */

angular.module('haikudepotserver').directive('jobStatusIndicator',[
    'messageSource','userState',
    function(messageSource,userState) {
        return {
            restrict: 'E',
            link : function($scope,element,attributes) {

                var valueExpression = attributes['job'];

                if(!valueExpression || !valueExpression.length) {
                    throw Error('the job expression must be supplied');
                }

                var containerE = angular.element('<span></span>');
                element.replaceWith(containerE);

                function updateText(job) {

                    containerE.attr('class','');
                    containerE.text('');

                    if(job && job.jobStatus) {

                        // when there is an error assembling the status text, this will
                        // be added instead.

                        function setErrorText() {
                            containerE.text('???');
                        }

                        function toTimestampStr(millis) {
                            return moment.utc(millis).local().format('YYYY-MM-DD HH:mm:ss')
                        }

                        function getStatusTimestamp() {
                            switch(job.jobStatus) {
                                case 'QUEUED': return job.queuedTimestamp;
                                case 'STARTED': return job.startTimestamp;
                                case 'FINISHED': return job.finishTimestamp;
                                case 'FAILED': return job.failTimestamp;
                                case 'CANCELLED': return job.cancelTimestamp;
                                default:
                                    throw Error('unknown state; ' + job.jobStatus);
                            }
                        }

                        function standardText(key,atMillis) {
                            messageSource.get(naturalLanguageCode, key).then(
                                function(str) {
                                    var str = str.replace('{0}',toTimestampStr(atMillis));

                                    if('STARTED' == job.jobStatus && !!job.progressPercent) {
                                        str += ' (' + job.progressPercent + '%)';
                                    }

                                    containerE.text(str);
                                },
                                function() {
                                    setErrorText();
                                }
                            );
                        }

                        containerE.addClass('job-status-indicator-' + job.jobStatus.toLowerCase());
                        var naturalLanguageCode = userState.naturalLanguageCode();

                        // TODO; do something more fancy later...

                        standardText(
                            'job.jobstatus.'+job.jobStatus.toLowerCase()+'.indicator',
                            getStatusTimestamp(job)
                        );

                    }

                }

                // TODO; watch the progress / state changes in the future?

                $scope.$watch(valueExpression, function(newValue) {
                    updateText(newValue);
                });

            }
        };
    }]);