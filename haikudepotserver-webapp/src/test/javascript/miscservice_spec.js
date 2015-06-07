/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

describe('miscService', function() {

    beforeEach(module('haikudepotserver'));

    var miscService;

    beforeEach(inject(function(_miscService_) {
        miscService = _miscService_;
    }));

    it('stripBase64FromDataUrl should handle various data url forms', function() {

        expect(miscService.stripBase64FromDataUrl('data:;base64,MUJP')).toBe("MUJP");
        expect(miscService.stripBase64FromDataUrl('data:image/png;base64,iVBORw0KGgoAAAANSUhEUg')).toBe("iVBORw0KGgoAAAANSUhEUg");
        expect(miscService.stripBase64FromDataUrl('data:base64,AAANSUhEUg')).toBe("AAANSUhEUg");
        expect(miscService.stripBase64FromDataUrl('')).toBe(undefined);

    });

    it('abbreviate should work', function() {

        var samples = [
            { original : 'Andrew Lindesay' },
            { original : 'G W' },
            { original : ' Ge Wizz' }
        ];

        miscService.abbreviate(samples,'original','abbreviated');

        expect(samples[0].abbreviated).toBe('An. Li.');
        expect(samples[1].abbreviated).toBe('G W');
        expect(samples[2].abbreviated).toBe('Ge Wi.');

    });

});