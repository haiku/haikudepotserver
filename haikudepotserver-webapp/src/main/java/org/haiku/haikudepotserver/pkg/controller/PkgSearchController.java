/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.mail.internet.MimeUtility;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.pkg.model.OpenSearchDescription;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

/**
 * <p>This controller provides for "open search" style information that is able to inform
 * search engines about undertaking search queries into this application server.  This can
 * be used for situations such as search bar in Firefox.</p>
 */

@Controller
@RequestMapping(value = {
        PkgSearchController.SEGMENT_SEARCH,
        PkgSearchController.SEGMENT_SEARCH_LEGACY // TODO; remove
})
public class PkgSearchController {

    private final static String KEY_QUERY = "srchexpr";

    final static String SEGMENT_SEARCH = "__pkgsearch";
    final static String SEGMENT_SEARCH_LEGACY = "pkgsearch";

    private final Configuration freemarkerConfiguration;
    private final ServerRuntime serverRuntime;
    private final MessageSource messageSource;
    private final PkgService pkgService;
    private final String baseUrl;
    private final String defaultArchitectureCode;
    private final Boolean isProduction;

    public PkgSearchController(
            ServerRuntime serverRuntime,
            MessageSource messageSource,
            PkgService pkgService,
            @Qualifier("opensearchFreemarkerConfiguration") Configuration freemarkerConfiguration,
            @Value("${hds.base-url}") String baseUrl,
            @Value("${hds.architecture.default.code}") String defaultArchitectureCode,
            @Value("${hds.deployment.is-production:false}") Boolean isProduction) {
        this.serverRuntime = serverRuntime;
        this.messageSource = messageSource;
        this.pkgService = pkgService;
        this.freemarkerConfiguration = freemarkerConfiguration;
        this.baseUrl = baseUrl;
        this.defaultArchitectureCode = defaultArchitectureCode;
        this.isProduction = isProduction;
    }

    /**
     * <p>This method returns XML data that defines how a browser might search the site.  It uses an
     * open-standard for this called "open search".</p>
     */

    @RequestMapping(value = "/opensearch.xml", method = RequestMethod.GET)
    public void handleOpenSearchDescription(
            HttpServletRequest request,
            HttpServletResponse response,
            Locale locale
    ) throws IOException {
        Preconditions.checkArgument(null != response);
        Preconditions.checkArgument(null != request);

        OpenSearchDescription model = new OpenSearchDescription();
        NaturalLanguageCoordinates naturalLanguageCoordinates = NaturalLanguageCoordinates.fromLocale(locale);

        model.setShortName(messageSource.getMessage(
                "opensearchdescription.shortname" + (isProduction ? "" : ".nonProductionDeploy"),
                new Object[] {},
                naturalLanguageCoordinates.toLocale()));
        model.setBaseUrl(baseUrl);
        model.setNaturalLanguageCoordinates(naturalLanguageCoordinates);
        model.setDescription(messageSource.getMessage(
                "opensearchdescription.description",
                new Object[] {},
                naturalLanguageCoordinates.toLocale()));

        BeansWrapper wrapper = BeansWrapper.getDefaultInstance();

        response.setContentType("application/opensearchdescription+xml");

        try {
            // It looks like the Freemarker loading system is only able to deal with the language code without
            // country or variants.
            Template template = freemarkerConfiguration.getTemplate("opensearchdescription-body_" + naturalLanguageCoordinates.getLanguageCode());
            template.process(wrapper.wrap(model), response.getWriter());
        }
        catch (TemplateException e) {
            throw new IllegalStateException("unable to generate the open search description", e);
        }

    }

    /**
     * <p>This method will try to find the identified package.  If it finds it then it will redirect to a view of
     * that package.  If it does not find it then it will redirect to a search page querying that expression.</p>
     */

    @RequestMapping(value = "search", method = RequestMethod.GET)
    public void handleSearch(
            HttpServletResponse response,
            @RequestParam(value = KEY_QUERY, required = false) String query) throws IOException {

        if (null != query) {
            query = query.trim().toLowerCase();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        Optional<PkgVersion> pkgVersionOptional = Optional.empty();

        if (!Strings.isNullOrEmpty(query) && Pkg.PATTERN_NAME.matcher(query).matches()) {
            ObjectContext context = serverRuntime.newContext();
            Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, query);

            if (pkgOptional.isPresent()) {
                Architecture architecture = Architecture.getByCode(context, defaultArchitectureCode);
                pkgVersionOptional = pkgService.getLatestPkgVersionForPkg(
                        context,
                        pkgOptional.get(),
                        Repository.getByCode(context, Repository.CODE_DEFAULT)
                                .tryGetRepositorySourceForArchitecture(architecture)
                                .orElseThrow(() -> new IllegalStateException("cannot find repository source")),
                        // ^ TODO - user interface for choosing?
                        Collections.singletonList(architecture));
            }
        }

        if(pkgVersionOptional.isPresent()) {

            PkgVersion pv = pkgVersionOptional.get();

            builder.pathSegment("#!", "pkg", pv.getPkg().getName());
            pv.toVersionCoordinates().appendPathSegments(builder);
            builder.pathSegment(pv.getArchitecture().getCode());
        } else {
            builder.path("#!/");
            builder.queryParam(KEY_QUERY, query);
        }

        String uri = builder.build().toUriString();

        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader(HttpHeaders.LOCATION, MimeUtility.encodeText(uri));
        response.setContentType(MediaType.PLAIN_TEXT_UTF_8.toString());

        PrintWriter printWriter = response.getWriter();
        printWriter.print(uri);
        printWriter.flush();
    }

}
