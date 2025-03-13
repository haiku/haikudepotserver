/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgSupplementModification;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@ContextConfiguration(classes = {TestAppConfig.class, TestServletConfig.class})
@WebAppConfiguration
public class PkgScreenshotControllerIT extends AbstractIntegrationTest {

    @Resource
    private PkgScreenshotController pkgScreenshotController;

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    /**
     * <p>This will return an image that can be used as a sample screenshot.</p>
     */

    private byte[] getScreenshotDataD() throws IOException {
        return getResourceData("sample-320x180-d.png");
    }

    private byte[] getScreenshotDataA() throws IOException {
        return getResourceData("sample-320x240-a.png");
    }

    @Test
    public void testPut() throws Exception {

        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();
        byte[] imageData = getScreenshotDataD();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContent(imageData);

        // ------------------------------------
        pkgScreenshotController.handleAdd(request, response, "png", "pkg1");
        // -----------------------------------   

        // the header should contain the image code
        String code = response.getHeader(PkgScreenshotController.HEADER_SCREENSHOTCODE);
        Assertions.assertThat(code).isNotEmpty();

        ObjectContext context = serverRuntime.newContext();
        PkgScreenshot screenshot = PkgScreenshot.getByCode(context, code);
        Assertions.assertThat(screenshot.getPkgSupplement().getBasePkgName()).isEqualTo("pkg1");
        Assertions.assertThat(screenshot.getPkgScreenshotImage().getData()).isEqualTo(imageData);

        Pkg pkg = Pkg.getByName(context, screenshot.getPkgSupplement().getBasePkgName());
        List<PkgSupplementModification> modifications = PkgSupplementModification.findForPkg(context, pkg);
        Assertions.assertThat(modifications.size()).isGreaterThanOrEqualTo(1);

        PkgSupplementModification modification = modifications.getLast();
        Assertions.assertThat(modification.getUser().getNickname()).isEqualTo("root");
        Assertions.assertThat(modification.getUserDescription()).isEqualTo("root");
        Assertions.assertThat(modification.getOriginSystemDescription()).isEqualTo("hds");
        Assertions.assertThat(modification.getContent()).isEqualTo(
                String.format("added screenshot [%s]; sh256 [%s]; height %d; width %d",
                        code,
                        screenshot.getHashSha256(),
                        screenshot.getHeight(),
                        screenshot.getWidth()));

    }

    @Test
    public void testGet_noScaling() throws Exception {

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        byte[] imageData = getScreenshotDataA();

        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        pkgScreenshotController.handleGet(
                response,
                640, 480,
                "png",
                data.pkg1.getPkgSupplement().getSortedPkgScreenshots().get(0).getCode());
        // -----------------------------------

        Assertions.assertThat(response.getContentType()).isEqualTo(MediaType.PNG.toString());
        HashCode responseData = Hashing.sha256().hashBytes(response.getContentAsByteArray());
        HashCode sourceImageData = Hashing.sha256().hashBytes(imageData);
        Assertions.assertThat(responseData).isEqualTo(sourceImageData);
    }

    @Test
    public void testGet_scaling() throws Exception {

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        pkgScreenshotController.handleGet(
                response,
                160, 120,
                "png",
                data.pkg1.getPkgSupplement().getSortedPkgScreenshots().get(0).getCode());
        // -----------------------------------

        Assertions.assertThat(response.getContentType()).isEqualTo(MediaType.PNG.toString());

        ImageHelper imageHelper = new ImageHelper();
        ImageHelper.Size size = imageHelper.derivePngSize(response.getContentAsByteArray());
        Assertions.assertThat(size.width).isEqualTo(160);
        Assertions.assertThat(size.height).isEqualTo(120);

    }

}
