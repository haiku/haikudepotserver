/*
 * Copyright 2014-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.api1.model.miscellaneous.*;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.UserRatingStability;
import org.haiku.haikudepotserver.feed.model.FeedService;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
public class MiscelaneousApiIT extends AbstractIntegrationTest {

    @Resource
    MiscellaneousApi miscellaneousApi;

    @Resource
    RuntimeInformationService runtimeInformationService;

    @Test
    public void testGetAllUserRatingStabilities() {

        // ------------------------------------
        GetAllUserRatingStabilitiesResult result = miscellaneousApi.getAllUserRatingStabilities(new GetAllUserRatingStabilitiesRequest());
        // ------------------------------------

        ObjectContext objectContext = serverRuntime.getContext();

        List<UserRatingStability> userRatingStabilities = UserRatingStability.getAll(objectContext);

        Assertions.assertThat(userRatingStabilities.size()).isEqualTo(result.userRatingStabilities.size());

        for (int i = 0; i < userRatingStabilities.size(); i++) {
            UserRatingStability userRatingStability = userRatingStabilities.get(i);
            GetAllUserRatingStabilitiesResult.UserRatingStability apiUserRatingStability = result.userRatingStabilities.get(i);
            Assertions.assertThat(userRatingStability.getCode()).isEqualTo(apiUserRatingStability.code);
            Assertions.assertThat(userRatingStability.getName()).isEqualTo(apiUserRatingStability.name);
        }
    }

    @Test
    public void testGetAllUserRatingStabilities_de() {

        GetAllUserRatingStabilitiesRequest request = new GetAllUserRatingStabilitiesRequest();
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;

        // ------------------------------------
        GetAllUserRatingStabilitiesResult result = miscellaneousApi.getAllUserRatingStabilities(request);
        // ------------------------------------

        Optional<GetAllUserRatingStabilitiesResult.UserRatingStability> userRatingStabilityOptional =
                result.userRatingStabilities.stream().filter(urs -> urs.code.equals("mostlystable")).findFirst();

        Assertions.assertThat(userRatingStabilityOptional.isPresent()).isTrue();
        Assertions.assertThat(userRatingStabilityOptional.get().name).isEqualTo("Ziemlich stabil");

    }

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

    /**
     * <p>If the client asks for all of the categories with a natural language code then they will be returned
     * the localized name of the category where possible.  This tests this with German as German translations
     * are known to be present.</p>
     */

    @Test
    public void testGetAllPkgCategories_de() {

        GetAllPkgCategoriesRequest request = new GetAllPkgCategoriesRequest();
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;

        // ------------------------------------
        GetAllPkgCategoriesResult result = miscellaneousApi.getAllPkgCategories(request);
        // ------------------------------------

        Optional<GetAllPkgCategoriesResult.PkgCategory> pkgCategoryOptional =
                result.pkgCategories.stream().filter(pks -> pks.code.equals("education")).findFirst();

        Assertions.assertThat(pkgCategoryOptional.isPresent()).isTrue();
        Assertions.assertThat(pkgCategoryOptional.get().name).isEqualTo("Bildung");
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

    /**
     * <p>It is possible to request the natural languages with a natural language code.  In this case, the
     * results will localize the names as opposed to using those onces directly from the database.</p>
     */

    @Test
    public void testGetAllNaturalLanguages_de() {

        GetAllNaturalLanguagesRequest request = new GetAllNaturalLanguagesRequest();
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;

        // ------------------------------------
        GetAllNaturalLanguagesResult result = miscellaneousApi.getAllNaturalLanguages(request);
        // ------------------------------------

        Optional<GetAllNaturalLanguagesResult.NaturalLanguage> naturalLanguageOptional =
                result.naturalLanguages.stream().filter(nl -> nl.code.equalsIgnoreCase("es")).findFirst();

        Assertions.assertThat(naturalLanguageOptional.isPresent()).isTrue();
        Assertions.assertThat(naturalLanguageOptional.get().name).isEqualTo("Espa\u00F1ol");

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
        return result.architectures.stream().filter(a -> a.code.equals(architectureCode)).findFirst();
    }

    @Test
    public void testGetAllArchitectures() {

        // ------------------------------------
        GetAllArchitecturesResult result = miscellaneousApi.getAllArchitectures(new GetAllArchitecturesRequest());
        // ------------------------------------

        // not sure what architectures there may be in the future, but
        // we will just check for a couple that we know to be there.

        Assertions.assertThat(isPresent(result, "x86_64").isPresent()).isTrue();
        Assertions.assertThat(isPresent(result, "x86_gcc2").isPresent()).isTrue();

        Assertions.assertThat(isPresent(result, "x86").isPresent()).isFalse();
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
        Assertions.assertThat(queryParams.get(FeedService.KEY_LIMIT)).isEqualTo("55");
        Assertions.assertThat(queryParams.get(FeedService.KEY_NATURALLANGUAGECODE)).isEqualTo(NaturalLanguage.CODE_GERMAN);
        Assertions.assertThat(queryParams.get(FeedService.KEY_PKGNAMES)).isEqualTo(String.join("-",data.pkg1.getName(), data.pkg2.getName()));
        Assertions.assertThat(queryParams.get(FeedService.KEY_TYPES)).isEqualTo(GenerateFeedUrlRequest.SupplierType.CREATEDPKGVERSION.name());

    }

}
