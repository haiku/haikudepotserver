/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.haikuos.haikudepotserver.api1.model.miscellaneous.*;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.PkgCategory;
import org.haikuos.haikudepotserver.feed.controller.FeedController;
import org.haikuos.haikudepotserver.support.RuntimeInformationService;
import org.junit.Test;

import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class MiscelaneousApiIT extends AbstractIntegrationTest {

    @Resource
    MiscellaneousApi miscellaneousApi;

    @Resource
    RuntimeInformationService runtimeInformationService;

    @Test
    public void testGetAllPkgCategories() {

        // ------------------------------------
        GetAllPkgCategoriesResult result = miscellaneousApi.getAllPkgCategories(new GetAllPkgCategoriesRequest());
        // ------------------------------------

        ObjectContext objectContext = serverRuntime.getContext();

        List<PkgCategory> pkgCategories = PkgCategory.getAll(objectContext);

        Assertions.assertThat(pkgCategories.size()).isEqualTo(result.pkgCategories.size());

        for (int i = 0; i < pkgCategories.size(); i++) {
            PkgCategory pkgCategory = pkgCategories.get(i);
            GetAllPkgCategoriesResult.PkgCategory apiPkgCategory = result.pkgCategories.get(i);
            Assertions.assertThat(pkgCategory.getName()).isEqualTo(apiPkgCategory.name);
            Assertions.assertThat(pkgCategory.getCode()).isEqualTo(apiPkgCategory.code);
        }
    }

    @Test
    public void testGetAllNaturalLanguages() {

        // ------------------------------------
        GetAllNaturalLanguagesResult result = miscellaneousApi.getAllNaturalLanguages(new GetAllNaturalLanguagesRequest());
        // ------------------------------------

        ObjectContext objectContext = serverRuntime.getContext();

        List<NaturalLanguage> naturalLanguages = NaturalLanguage.getAll(objectContext);

        Assertions.assertThat(naturalLanguages.size()).isEqualTo(result.naturalLanguages.size());

        for (int i = 0; i < naturalLanguages.size(); i++) {
            NaturalLanguage naturalLanguage = naturalLanguages.get(i);
            GetAllNaturalLanguagesResult.NaturalLanguage apiNaturalLanguage = result.naturalLanguages.get(i);
            Assertions.assertThat(naturalLanguage.getName()).isEqualTo(apiNaturalLanguage.name);
            Assertions.assertThat(naturalLanguage.getCode()).isEqualTo(apiNaturalLanguage.code);
        }
    }

    @Test
    public void getRuntimeInformation_asUnauthenticated() {

        // ------------------------------------
        GetRuntimeInformationResult result = miscellaneousApi.getRuntimeInformation(new GetRuntimeInformationRequest());
        // ------------------------------------

        Assertions.assertThat(result.javaVersion).isNull();
    }

    @Test
    public void getRuntimeInformation_asRoot() {

        setAuthenticatedUserToRoot();

        // ------------------------------------
        GetRuntimeInformationResult result = miscellaneousApi.getRuntimeInformation(new GetRuntimeInformationRequest());
        // ------------------------------------

        Assertions.assertThat(result.javaVersion).isEqualTo(runtimeInformationService.getJavaVersion());

    }

    @Test
    public void testGetAllMessages() throws Exception {

        // ------------------------------------
        GetAllMessagesResult result = miscellaneousApi.getAllMessages(new GetAllMessagesRequest(NaturalLanguage.CODE_ENGLISH));
        // ------------------------------------

        Assertions.assertThat(result.messages.get("test.it")).isEqualTo("Test line for integration testing");

    }

    private Optional<GetAllArchitecturesResult.Architecture> isPresent(
            GetAllArchitecturesResult result,
            final String architectureCode) {
        return Iterables.tryFind(result.architectures, new Predicate<GetAllArchitecturesResult.Architecture>() {
            @Override
            public boolean apply(GetAllArchitecturesResult.Architecture architecture) {
                return architecture.code.equals(architectureCode);
            }
        });
    }

    @Test
    public void testGetAllArchitectures() {

        // ------------------------------------
        GetAllArchitecturesResult result = miscellaneousApi.getAllArchitectures(new GetAllArchitecturesRequest());
        // ------------------------------------

        // not sure what architectures there may be in the future, but
        // we will just check for a couple that we know to be there.

        Assertions.assertThat(isPresent(result, "x86").isPresent()).isTrue();
        Assertions.assertThat(isPresent(result, "x86_gcc2").isPresent()).isTrue();
        Assertions.assertThat(isPresent(result, "mips").isPresent()).isFalse();
    }

    @Test
    public void testGenerateFeedUrl() throws ObjectNotFoundException, MalformedURLException {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GenerateFeedUrlRequest request = new GenerateFeedUrlRequest();
        request.limit = 55;
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;
        request.pkgNames = ImmutableList.of(data.pkg1.getName(), data.pkg2.getName());
        request.supplierTypes = ImmutableList.of(GenerateFeedUrlRequest.SupplierType.CREATEDPKGVERSION);

        // ------------------------------------
        String urlString = miscellaneousApi.generateFeedUrl(request).url;
        // ------------------------------------

        URL url = new URL(urlString);

        Assertions.assertThat(url.getPath()).endsWith("/feed/pkg.atom");

        // this is a bit rough, but will do for assertion...
        Map<String,String> queryParams = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(url.getQuery());
        Assertions.assertThat(queryParams.get(FeedController.KEY_LIMIT)).isEqualTo("55");
        Assertions.assertThat(queryParams.get(FeedController.KEY_NATURALLANGUAGECODE)).isEqualTo(NaturalLanguage.CODE_GERMAN);
        Assertions.assertThat(queryParams.get(FeedController.KEY_PKGNAMES)).isEqualTo(Joiner.on('-').join(ImmutableList.of(data.pkg1.getName(), data.pkg2.getName())));
        Assertions.assertThat(queryParams.get(FeedController.KEY_TYPES)).isEqualTo(GenerateFeedUrlRequest.SupplierType.CREATEDPKGVERSION.name());

    }

}
