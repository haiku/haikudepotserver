/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive loads details from the server and then displays the
 * constraints that are required when entering a password.</p>
 */

angular.module('haikudepotserver').directive('passwordRequirements',function() {
    return {
      restrict: 'E',
      templateUrl:'/__js/app/directivetemplate/passwordrequirements.html',
      replace: true,
      controller:
        ['$scope', 'remoteProcedureCall', 'constants', 'errorHandling',
          function ($scope, remoteProcedureCall, constants, errorHandling) {

            $scope.messageParameters = null;

            remoteProcedureCall.call(constants.ENDPOINT_API_V2_USER, "get-password-requirements")
              .then(
                function (passwordRequirements) {
                  $scope.messageParameters = [
                    passwordRequirements.minPasswordLength,
                    passwordRequirements.minPasswordUppercaseChar,
                    passwordRequirements.minPasswordDigitsChar
                  ];
                },
                errorHandling.handleRemoteProcedureCallError
              );
          }
        ]
    };
  }
);
