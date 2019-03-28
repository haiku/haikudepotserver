/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AddAuthorizationPkgRuleController',
    [
        '$scope','$log',
        'jsonRpc','constants','breadcrumbs','breadcrumbFactory','errorHandling','messageSource',
        'userState',
        function(
            $scope,$log,
            jsonRpc,constants,breadcrumbs,breadcrumbFactory,errorHandling,messageSource,
            userState) {

            $scope.pkgNamePattern = ('' + constants.PATTERN_PKG_NAME).replace(/^\//,'').replace(/\/$/,'');
            $scope.userNicknamePattern = ('' + constants.PATTERN_USER_NICKNAME).replace(/^\//,'').replace(/\/$/,'');

            // This var will be true if the rule that the operator was attempting to create conflicted
            // with an existing rule that was already there.

            $scope.wasConflicting = false;

            // It seems a bit poor to hard code these permissions, but we actually only want to allow certain
            // permissions to be used here.

            $scope.allPermissions = _.map(
                [
                    'pkg_editicon',
                    'pkg_editscreenshot',
                    'pkg_editcategories',
                    'pkg_editprominence',
                    'pkg_editlocalization',
                    'pkg_editchangelog'
                ],
                function(permissionCode) {
                    var result = {
                        code : permissionCode,
                        title : '...'
                    };

                    messageSource.get(userState.naturalLanguageCode(),'permission.' + permissionCode + '.title').then(
                        function(value) {
                            result.title = value;
                        },
                        function() {
                            result.title = '???';
                        }
                    );

                    return result;
                }
            );

            $scope.workingRule = {
                userNickname : undefined,
                permission : $scope.allPermissions[0],
                authorizationTargetScopeType : 'APKG'
            };

            var amSaving = false;

            $scope.shouldSpin = function() {
                return amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.addRuleForm[name] && $scope.addRuleForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            /**
             * <p>This function gets hit when the user nickname changes.  It may be that a prior attempt to create
             * the rule was using a user nickname that did not exist etc... and so this will create validation
             * failures that cannot be tested until the user submits the form.  To allow them to try again, any
             * mutation of the data will reset the validation.</p>
             */

            $scope.userNicknameDidChange = function() {
                $scope.addRuleForm.userNickname.$setValidity('root',true);
                $scope.addRuleForm.userNickname.$setValidity('notfound',true);
            };

            $scope.pkgNameDidChange = function() {
                $scope.addRuleForm.pkgName.$setValidity('notfound',true);
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListAuthorizationPkgRules(),
                    breadcrumbFactory.createAddAuthorizationPkgRule()
                ]);
            }

            refreshBreadcrumbItems();

            /**
             * <p>This function gets hit when the user chooses to save a rule.</p>
             */

            $scope.goAddRule = function() {

                amSaving = true;
                $scope.wasConflicting = false;

                if ($scope.addRuleForm.$invalid) {
                    throw Error('it is not possible to add a rule if the form is invalid.');
                }

                var request = {
                    userNickname : $scope.workingRule.userNickname,
                    permissionCode : $scope.workingRule.permission.code,
                    pkgName : undefined
                };

                if ('APKG' === $scope.workingRule.authorizationTargetScopeType) {
                    request.pkgName = $scope.workingRule.pkgName;
                }

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_AUTHORIZATION,
                    "createAuthorizationPkgRule",
                    [request]
                ).then(
                    function() {
                        $log.info('did create authorization pkg rule');
                        breadcrumbs.popAndNavigate();
                    },
                    function(err) {

                        switch(err.code) {

                            case jsonRpc.errorCodes.AUTHORIZATIONRULECONFLICT:
                                $scope.wasConflicting = true;
                                break;

                            case jsonRpc.errorCodes.VALIDATION:
                                _.each(
                                    err.data,
                                    function(item) {
                                        if ('user' === item.property) {
                                            $scope.addRuleForm.userNickname.$setValidity(item.message,false);
                                        }
                                        else {
                                            errorHandling.handleJsonRpcError(err);
                                        }
                                    }
                                );
                                break;

                            case jsonRpc.errorCodes.OBJECTNOTFOUND:

                                switch(err.data.entityName) {

                                    case 'User':
                                        $scope.addRuleForm.userNickname.$setValidity('notfound',false);
                                        break;

                                    case 'Pkg':
                                        $scope.addRuleForm.pkgName.$setValidity('notfound',false);
                                        break;

                                    default:
                                        errorHandling.handleJsonRpcError(err);
                                        break;

                                }

                                break;

                            default:
                                errorHandling.handleJsonRpcError(err);
                                break;

                        }

                    }
                )
                    .finally(function() {
                        amSaving = false;
                    });

            }

        }
    ]
);