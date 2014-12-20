/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.controller;

import com.google.common.net.MediaType;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.IOException;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class PkgIconControllerIT extends AbstractIntegrationTest {

    @Resource
    PkgIconController pkgIconController;

    @Resource
    IntegrationTestSupportService integrationTestSupportService;

    private byte[] getIconData() throws IOException {
        return getResourceData("/sample-32x32.png");
    }

    /**
     * <p>This test works knowing that the test package pkg1 will have a PNG image pre-loaded for it.</p>
     */

    @Test
    public void testGet() throws Exception {

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        byte[] imageData = getIconData();

        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        pkgIconController.handleGet(
                response,
                32,
                "png",
                "pkg1",
                true);
        // -----------------------------------

        Assertions.assertThat(response.getContentType()).isEqualTo(MediaType.PNG.toString());
        Assertions.assertThat(response.getContentAsByteArray()).isEqualTo(imageData);

    }

}
