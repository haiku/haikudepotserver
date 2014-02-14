/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.pkg.controller;

import com.google.common.base.Optional;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.pkg.controller.PkgIconController;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.haikuos.haikudepotsever.api1.support.IntegrationTestSupportService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.annotation.Resource;
import java.io.IOException;

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

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        pkgIconController.fetchGet(
                request, response,
                32,
                "png",
                "pkg1");
        // -----------------------------------

        Assertions.assertThat(response.getContentType()).isEqualTo(MediaType.PNG.toString());
        Assertions.assertThat(response.getContentAsByteArray()).isEqualTo(imageData);

    }

    @Test
    public void testPut() throws Exception {

        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        byte[] imageData = getIconData();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(imageData);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
         pkgIconController.put(
                 request,response,
                 32,
                 "png",
                 "pkg2");
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.getContext();
            Optional<Pkg> pkgOptional = Pkg.getByName(context,"pkg2");
            Optional<org.haikuos.haikudepotserver.dataobjects.MediaType> mediaTypeOptional = org.haikuos.haikudepotserver.dataobjects.MediaType.getByCode(context,MediaType.PNG.toString());
            Assertions.assertThat(pkgOptional.get().getPkgIcon(mediaTypeOptional.get(),32).get().getPkgIconImage().get().getData()).isEqualTo(imageData);
        }

    }

}
