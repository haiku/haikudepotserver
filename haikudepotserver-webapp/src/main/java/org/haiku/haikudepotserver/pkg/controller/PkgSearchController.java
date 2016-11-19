/*
 * Copyright 2015-2016, Andrew Lindesay
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
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.pkg.model.OpenSearchDescription;
import org.haiku.haikudepotserver.support.web.NaturalLanguageWebHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
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

    public final static String SEGMENT_SEARCH = "__pkgsearch";
    public final static String SEGMENT_SEARCH_LEGACY = "pkgsearch";

    @Resource(name = "opensearchFreemarkerConfiguration")
    private Configuration freemarkerConfiguration;

    @Resource
    private ServerRuntime serverRuntime;

    @Value("${baseurl}")
    private String baseUrl;

    @Value("${architecture.default.code}")
    private String defaultArchitectureCode;

    @Value("${deployment.isproduction:false}")
    private Boolean isProduction;

    @Resource
    private MessageSource messageSource;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    /**
     * <p>This method returns XML data that defines how a browser might search the site.  It uses an
     * open-standard for this called "open search".</p>
     */

    @RequestMapping(value = "/opensearch.xml", method = RequestMethod.GET)
    public void handleOpenSearchDescription(
            HttpServletResponse response,
            HttpServletRequest request
    ) throws IOException {
        Preconditions.checkArgument(null!=response);
        Preconditions.checkArgument(null!=request);

        ObjectContext context = serverRuntime.getContext();
        OpenSearchDescription model = new OpenSearchDescription();
        NaturalLanguage naturalLanguage = NaturalLanguageWebHelper.deriveNaturalLanguage(context, request);

        model.setShortName(messageSource.getMessage(
                "opensearchdescription.shortname" + (isProduction ? "" : ".nonProductionDeploy"),
                new Object[] {},
                naturalLanguage.toLocale()));
        model.setBaseUrl(baseUrl);
        model.setNaturalLanguageCode(naturalLanguage.getCode());
        model.setDescription(messageSource.getMessage(
                "opensearchdescription.description",
                new Object[] {},
                naturalLanguage.toLocale()));

        BeansWrapper wrapper = BeansWrapper.getDefaultInstance();

        response.setContentType("application/opensearchdescription+xml");

        try {
            Template template = freemarkerConfiguration.getTemplate("opensearchdescription-body_" + naturalLanguage.getCode());
            template.process(wrapper.wrap(model), response.getWriter());
        }
        catch(TemplateException e) {
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

        if(null!=query) {
            query = query.trim().toLowerCase();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        Optional<PkgVersion> pkgVersionOptional = Optional.empty();

        if(!Strings.isNullOrEmpty(query) && Pkg.PATTERN_NAME.matcher(query).matches()) {
            ObjectContext context = serverRuntime.getContext();
            Optional<Pkg> pkgOptional = Pkg.getByName(context, query);

            if(pkgOptional.isPresent()) {
                pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                        context,
                        pkgOptional.get(),
                        Repository.getByCode(context, Repository.CODE_DEFAULT).get(), // TODO - user interface for choosing?
                        Collections.singletonList(Architecture.getByCode(context, defaultArchitectureCode).get()));
            }
        }

        if(pkgVersionOptional.isPresent()) {

            PkgVersion pv = pkgVersionOptional.get();

            builder.pathSegment("#", "pkg", pv.getPkg().getName());
            pv.toVersionCoordinates().appendPathSegments(builder);
            builder.pathSegment(pv.getArchitecture().getCode());
        }
        else {
            builder.path("#/");
            builder.queryParam(KEY_QUERY, query);
        }

        String uri = builder.build().toUriString();

        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader(HttpHeaders.LOCATION, uri);
        response.setContentType(MediaType.PLAIN_TEXT_UTF_8.toString());

        PrintWriter printWriter = response.getWriter();
        printWriter.print(uri);
        printWriter.flush();
    }

}
