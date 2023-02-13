/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import com.google.common.base.Splitter;
import com.nimbusds.jose.util.Pair;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api2.model.Architecture;
import org.haiku.haikudepotserver.api2.model.GenerateFeedUrlRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GenerateFeedUrlResult;
import org.haiku.haikudepotserver.api2.model.GenerateFeedUrlSupplierType;
import org.haiku.haikudepotserver.api2.model.GetAllArchitecturesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllArchitecturesResult;
import org.haiku.haikudepotserver.api2.model.GetAllMessagesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllMessagesResult;
import org.haiku.haikudepotserver.api2.model.GetAllNaturalLanguagesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllNaturalLanguagesResult;
import org.haiku.haikudepotserver.api2.model.GetAllPkgCategoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllPkgCategoriesResult;
import org.haiku.haikudepotserver.api2.model.GetAllUserRatingStabilitiesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetAllUserRatingStabilitiesResult;
import org.haiku.haikudepotserver.api2.model.GetRuntimeInformationResult;
import org.haiku.haikudepotserver.api2.model.Message;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.UserRatingStability;
import org.haiku.haikudepotserver.dataobjects.auto._NaturalLanguage;
import org.haiku.haikudepotserver.feed.model.FeedService;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ContextConfiguration(classes = TestConfig.class)
public class MiscelaneousApiServiceIT extends AbstractIntegrationTest {

    @Resource
    MiscellaneousApiService miscellaneousApiService;

    @Resource
    RuntimeInformationService runtimeInformationService;

    @Test
    public void testGetAllUserRatingStabilities() {

        GetAllUserRatingStabilitiesRequestEnvelope request = new GetAllUserRatingStabilitiesRequestEnvelope()
                .naturalLanguageCode("en");

        // ------------------------------------
        GetAllUserRatingStabilitiesResult result = miscellaneousApiService.getAllUserRatingStabilities(request);
        // ------------------------------------

        ObjectContext objectContext = serverRuntime.newContext();

        List<UserRatingStability> userRatingStabilities = UserRatingStability.getAll(objectContext);

        Assertions.assertThat(userRatingStabilities.size()).isEqualTo(result.getUserRatingStabilities().size());

        Set<String> matchedCodes = Stream.of(
                        Pair.of("nostart", "Will not start up"),
                        Pair.of("veryunstable", "Very unstable"))
                .filter(p -> result.getUserRatingStabilities().stream()
                        .anyMatch(nl -> nl.getCode().equals(p.getLeft())
                                && nl.getName().equals(p.getRight())))
                .map(Pair::getLeft)
                .collect(Collectors.toSet());

        Assertions.assertThat(matchedCodes).containsOnly("nostart", "veryunstable");
    }

    @Test
    public void testGetAllUserRatingStabilities_de() {

        GetAllUserRatingStabilitiesRequestEnvelope request = new GetAllUserRatingStabilitiesRequestEnvelope()
                .naturalLanguageCode("de");

        // ------------------------------------
        GetAllUserRatingStabilitiesResult result = miscellaneousApiService.getAllUserRatingStabilities(request);
        // ------------------------------------

        Optional<org.haiku.haikudepotserver.api2.model.UserRatingStability> userRatingStabilityOptional =
                result.getUserRatingStabilities().stream().filter(urs -> urs.getCode().equals("mostlystable")).findFirst();

        Assertions.assertThat(userRatingStabilityOptional.isPresent()).isTrue();
        Assertions.assertThat(userRatingStabilityOptional.get().getName()).isEqualTo("Ziemlich stabil");

    }

    @Test
    public void testGetAllPkgCategories() {

        GetAllPkgCategoriesRequestEnvelope request = new GetAllPkgCategoriesRequestEnvelope()
                .naturalLanguageCode("en");

        // ------------------------------------
        GetAllPkgCategoriesResult result = miscellaneousApiService.getAllPkgCategories(request);
        // ------------------------------------

        ObjectContext objectContext = serverRuntime.newContext();

        List<PkgCategory> pkgCategories = PkgCategory.getAll(objectContext);

        Assertions.assertThat(pkgCategories.size()).isEqualTo(result.getPkgCategories().size());

        for (int i = 0; i < pkgCategories.size(); i++) {
            PkgCategory pkgCategory = pkgCategories.get(i);
            org.haiku.haikudepotserver.api2.model.PkgCategory apiPkgCategory = result.getPkgCategories().get(i);
            Assertions.assertThat(pkgCategory.getName()).isEqualTo(apiPkgCategory.getName());
            Assertions.assertThat(pkgCategory.getCode()).isEqualTo(apiPkgCategory.getCode());
        }
    }

    /**
     * <p>If the client asks for all of the categories with a natural language code then they will be returned
     * the localized name of the category where possible.  This tests this with German as German translations
     * are known to be present.</p>
     */

    @Test
    public void testGetAllPkgCategories_de() {

        GetAllPkgCategoriesRequestEnvelope request = new GetAllPkgCategoriesRequestEnvelope()
                .naturalLanguageCode("de");

        // ------------------------------------
        GetAllPkgCategoriesResult result = miscellaneousApiService.getAllPkgCategories(request);
        // ------------------------------------

        Optional<org.haiku.haikudepotserver.api2.model.PkgCategory> pkgCategoryOptional =
                result.getPkgCategories().stream().filter(pks -> pks.getCode().equals("education")).findFirst();

        Assertions.assertThat(pkgCategoryOptional.isPresent()).isTrue();
        Assertions.assertThat(pkgCategoryOptional.get().getName()).isEqualTo("Bildung");
    }

    @Test
    public void testGetAllNaturalLanguages() {

        GetAllNaturalLanguagesRequestEnvelope request = new GetAllNaturalLanguagesRequestEnvelope()
                .naturalLanguageCode("en");

        // ------------------------------------
        GetAllNaturalLanguagesResult result = miscellaneousApiService.getAllNaturalLanguages(request);
        // ------------------------------------

        ObjectContext objectContext = serverRuntime.newContext();

        List<NaturalLanguage> naturalLanguages = NaturalLanguage.getAll(objectContext);

        Assertions.assertThat(naturalLanguages.size()).isEqualTo(result.getNaturalLanguages().size());

        Set<String> matchedCodes = Stream.of(
                        Pair.of("ca", "Català; Valencià"),
                        Pair.of("ja", "日本語"))
                .filter(p -> result.getNaturalLanguages().stream()
                        .anyMatch(nl -> nl.getCode().equals(p.getLeft())
                                && nl.getName().equals(p.getRight())))
                .map(Pair::getLeft)
                .collect(Collectors.toSet());

        Assertions.assertThat(matchedCodes).containsOnly("ca", "ja");
    }

    /**
     * <p>It is possible to request the natural languages with a natural language code.  In this case, the
     * results will localize the names as opposed to using those onces directly from the database.</p>
     */

    @Test
    public void testGetAllNaturalLanguages_de() {

        GetAllNaturalLanguagesRequestEnvelope request = new GetAllNaturalLanguagesRequestEnvelope()
                .naturalLanguageCode("de");

        // ------------------------------------
        GetAllNaturalLanguagesResult result = miscellaneousApiService.getAllNaturalLanguages(request);
        // ------------------------------------

        Optional<org.haiku.haikudepotserver.api2.model.NaturalLanguage> naturalLanguageOptional =
                result.getNaturalLanguages().stream().filter(nl -> nl.getCode().equalsIgnoreCase("es")).findFirst();

        Assertions.assertThat(naturalLanguageOptional.isPresent()).isTrue();
        Assertions.assertThat(naturalLanguageOptional.get().getName()).isEqualTo("Espa\u00F1ol");

    }

    @Test
    public void getRuntimeInformation_asUnauthenticated() {

        // ------------------------------------
        GetRuntimeInformationResult result = miscellaneousApiService.getRuntimeInformation();
        // ------------------------------------

        Assertions.assertThat(result.getJavaVersion()).isNull();
    }

    @Test
    public void getRuntimeInformation_asRoot() {

        setAuthenticatedUserToRoot();

        // ------------------------------------
        GetRuntimeInformationResult result = miscellaneousApiService.getRuntimeInformation();
        // ------------------------------------

        Assertions.assertThat(result.getJavaVersion()).isEqualTo(runtimeInformationService.getJavaVersion());

    }

    @Test
    public void testGetAllMessages() {

        GetAllMessagesRequestEnvelope request = new GetAllMessagesRequestEnvelope()
                .naturalLanguageCode("en");

        // ------------------------------------
        GetAllMessagesResult result = miscellaneousApiService.getAllMessages(request);
        // ------------------------------------

        Message message = result.getMessages().stream()
                .filter(m -> m.getKey().equals("test.it"))
                .findFirst()
                .orElseThrow();
        Assertions.assertThat(message.getValue()).isEqualTo("Test line for integration testing");

    }

    @Test
    public void testGetAllArchitectures() {

        GetAllArchitecturesRequestEnvelope request = new GetAllArchitecturesRequestEnvelope()
                .naturalLanguageCode("en");

        // ------------------------------------
        GetAllArchitecturesResult result = miscellaneousApiService.getAllArchitectures(request);
        // ------------------------------------

        // not sure what architectures there may be in the future, but
        // we will just check for a couple that we know to be there.

        Set<String> architectureCodes = result.getArchitectures().stream().map(Architecture::getCode).collect(Collectors.toSet());
        Assertions.assertThat(architectureCodes).containsOnly("x86_64", "x86_gcc2");
    }

    @Test
    public void testGenerateFeedUrl() throws MalformedURLException {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GenerateFeedUrlRequestEnvelope request = new GenerateFeedUrlRequestEnvelope()
                .limit(55)
                .naturalLanguageCode("de")
                .pkgNames(List.of("pkg1", "pkg2"))
                .supplierTypes(List.of(GenerateFeedUrlSupplierType.CREATEDPKGVERSION));

        // ------------------------------------
        GenerateFeedUrlResult result = miscellaneousApiService.generateFeedUrl(request);
        // ------------------------------------

        URL url = new URL(result.getUrl());

        Assertions.assertThat(url.getPath()).endsWith("/__feed/pkg.atom");

        // this is a bit rough, but will do for assertion...
        Map<String,String> queryParams = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(url.getQuery());
        Assertions.assertThat(queryParams.get(FeedService.KEY_LIMIT)).isEqualTo("55");
        Assertions.assertThat(queryParams.get(FeedService.KEY_NATURALLANGUAGECODE)).isEqualTo(NaturalLanguage.CODE_GERMAN);
        Assertions.assertThat(queryParams.get(FeedService.KEY_PKGNAMES)).isEqualTo(String.join("-",data.pkg1.getName(), data.pkg2.getName()));
        Assertions.assertThat(queryParams.get(FeedService.KEY_TYPES)).isEqualTo(GenerateFeedUrlSupplierType.CREATEDPKGVERSION.name());

    }

}
