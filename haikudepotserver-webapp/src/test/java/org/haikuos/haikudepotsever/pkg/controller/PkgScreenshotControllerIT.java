/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.pkg.controller;

import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.dataobjects.PkgScreenshot;
import org.haikuos.haikudepotserver.pkg.controller.PkgScreenshotController;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.ImageHelper;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.haikuos.haikudepotsever.api1.support.IntegrationTestSupportService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;

public class PkgScreenshotControllerIT extends AbstractIntegrationTest {

    @Resource
    PkgScreenshotController pkgScreenshotController;

    @Resource
    IntegrationTestSupportService integrationTestSupportService;

    /**
     * <p>This will return an image that can be used as a sample screenshot.</p>
     */

    private byte[] getScreenshotData() throws IOException {
        return getResourceData("/sample-320x240.png");
    }

    @Test
    public void testPut() throws Exception {

        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        byte[] imageData = getScreenshotData();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContent(imageData);

        // ------------------------------------
        pkgScreenshotController.handleAdd(request, response, "png", "pkg1");
        // -----------------------------------   

        // the header should contain the image code
        String code = response.getHeader(PkgScreenshotController.HEADER_SCREENSHOTCODE);
        Assertions.assertThat(code).isNotEmpty();

        ObjectContext context = serverRuntime.getContext();
        Optional<PkgScreenshot> screenshotOptional = PkgScreenshot.getByCode(context, code);
        Assertions.assertThat(screenshotOptional.isPresent()).isTrue();
        Assertions.assertThat(screenshotOptional.get().getPkg().getName()).isEqualTo("pkg1");
        Assertions.assertThat(screenshotOptional.get().getPkgScreenshotImage().get().getData()).isEqualTo(imageData);

    }

    @Test
    public void testGet_noScaling() throws Exception {

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        byte[] imageData = getScreenshotData();

        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        pkgScreenshotController.handleGet(
                response,
                640, 480,
                "png",
                data.pkg1.getSortedPkgScreenshots().get(0).getCode());
        // -----------------------------------

        Assertions.assertThat(response.getContentType()).isEqualTo(MediaType.PNG.toString());
        Assertions.assertThat(response.getContentAsByteArray()).isEqualTo(imageData);

    }

    @Test
    public void testGet_scaling() throws Exception {

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        byte[] imageData = getScreenshotData();

        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        pkgScreenshotController.handleGet(
                response,
                160, 120,
                "png",
                data.pkg1.getSortedPkgScreenshots().get(0).getCode());
        // -----------------------------------

        Assertions.assertThat(response.getContentType()).isEqualTo(MediaType.PNG.toString());

        ImageHelper imageHelper = new ImageHelper();
        ImageHelper.Size size = imageHelper.derivePngSize(response.getContentAsByteArray());
        Assertions.assertThat(size.width).isEqualTo(160);
        Assertions.assertThat(size.height).isEqualTo(120);

    }

}
