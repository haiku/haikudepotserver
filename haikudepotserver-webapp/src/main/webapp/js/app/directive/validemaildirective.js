/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will check to make sure that the email entered in is in the correct format.  This is not
 * done with a regex as the regex gets quite complex.  The logic is also very procedural in order to be
 * straight-forward to read.</p>
 */

angular.module('haikudepotserver').directive('validEmail',function() {
    return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope,elem, attr, ctrl) { // ctrl = ngModel

            function isValid(value) {

                function isAlphaNumeric(charCode) {
                     return (charCode >= 65 && charCode <= 90) || // A-Z
                         (charCode >= 97 && charCode <= 122) || // a-z
                         (charCode >= 48 && charCode <= 57); // 0-9
                }

                /**
                 * <p>This returns true if the character given is legal for the mailbox portion of an email address.
                 * It does not consider the full-stop because it has special requirements.</p>
                 */

                // https://en.wikipedia.org/wiki/Email_box#Valid_characters

                function isGeneralLegalMailboxChar(charCode) {
                    if(isAlphaNumeric(charCode)) {
                        return true;
                    }

                    switch(charCode) {
                        case 33: // !
                        case 35: // #
                        case 36: // $
                        case 37: // %
                        case 38: // &
                        case 39: // '
                        case 42: // *
                        case 43: // +
                        case 45: // -
                        case 47: // /
                        case 61: // =
                        case 63: // ?
                        case 94: // ^
                        case 95: // _
                        case 96: // `
                        case 123: // {
                        case 124: // |
                        case 125: // }
                        case 126: // ~
                            return true;

                        default:
                            return false;
                    }
                }

                var atI = value.indexOf('@');

                if(-1==atI) {
                    return false;
                }

                // check the mailbox portion.

                var mailbox = value.substring(0,atI);

                if(0==mailbox.length) {
                    return false;
                }

                if(-1 != mailbox.indexOf('..')) {
                    return false;
                }

                for(var i=0;i<mailbox.length;i++) {
                    var cc = mailbox.charCodeAt(i);

                    if( (46==cc && (0==i || (mailbox.length-1==i))) && // fullstop handling
                        !isGeneralLegalMailboxChar(cc) ) {
                        return false;
                    }
                }

                // check the domain portion.

                var domainParts = value.substring(atI+1).split('.');

                if(domainParts.length < 2) {
                    return false;
                }

                // check the parts of the domain for being correct.

                for(var j=0;j<domainParts.length;j++) {
                    if (!domainParts[j].length) {
                        return false;
                    }

                    for (var i = 0; i < domainParts[j].length; i++) {
                        var cc = domainParts[j].charCodeAt(i);

                        if(45==cc) { // hyphen
                            if(0==i || (domainParts[j].length - 1 == i)) {
                                return false;
                            }
                        }
                        else {
                            if(!isAlphaNumeric(cc)) {
                                return false;
                            }
                        }

                    }
                }

                return true;
            }

            ctrl.$parsers.unshift(function(value) {

                if(value) {

                    value = '' + value;
                    value = value.trim();

                    if(value.length) {
                        ctrl.$setValidity('validEmail', isValid(value));
                        return value;
                    }
                }

                ctrl.$setValidity('validEmail', true);
                return undefined;
            });

        }
    };
});