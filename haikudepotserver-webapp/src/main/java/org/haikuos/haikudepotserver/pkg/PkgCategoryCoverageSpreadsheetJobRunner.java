/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSink;
import org.haikuos.haikudepotserver.pkg.model.PkgCategoryCoverageSpreadsheetJobSpecification;
import org.haikuos.haikudepotserver.support.Callback;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;

/**
 * <p>This report is a spreadsheet that covers basic details of each package.</p>
 */

@Component
public class PkgCategoryCoverageSpreadsheetJobRunner extends AbstractJobRunner<PkgCategoryCoverageSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgCategoryCoverageSpreadsheetJobRunner.class);

    /**
     * <p>This string is inserted into a cell in order to indicate that the combination of the
     * package on the row and the category on the column are true.</p>
     */

    private static String MARKER = "*";

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    /**
     * <p>This will go out and find the first package localization it can find on the latest package
     * versions.</p>
     */

    private Optional<PkgVersionLocalization> getAnyPkgVersionLocalizationForPkg(ObjectContext context, Pkg pkg) {

        List<Expression> expressions = Lists.newArrayList();

        expressions.add(ExpressionFactory.matchExp(
                PkgVersionLocalization.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY + ".",
                pkg));

        expressions.add(ExpressionFactory.matchExp(
                PkgVersionLocalization.PKG_VERSION_PROPERTY + "." + PkgVersion.IS_LATEST_PROPERTY,
                true));

        expressions.add(ExpressionFactory.matchExp(
                PkgVersionLocalization.PKG_VERSION_PROPERTY + "." + PkgVersion.ACTIVE_PROPERTY,
                true));

        SelectQuery query = new SelectQuery(
                PkgVersionLocalization.class,
                ExpressionHelper.andAll(expressions));

        List<PkgVersionLocalization> locs = (List<PkgVersionLocalization>) context.performQuery(query);

        if(locs.isEmpty()) {
            return Optional.absent();
        }

        return Optional.of(locs.get(0));
    }

    @Override
    public void run(
            JobOrchestrationService jobOrchestrationService,
            PkgCategoryCoverageSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null!=jobOrchestrationService);
        assert null!=jobOrchestrationService;
        Preconditions.checkArgument(null!=specification);

        final ObjectContext context = serverRuntime.getContext();

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobOrchestrationService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString());

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter, ',')
        ) {

            // headers

            final List<String> pkgCategoryCodes = Lists.transform(
                    PkgCategory.getAll(context),
                    new Function<PkgCategory, String>() {
                        @Override
                        public String apply(PkgCategory input) {
                            return input.getCode();
                        }
                    }
            );

            List<String> headings = Lists.newArrayList();
            headings.add("pkg-name");
            headings.add("any-summary");
            headings.add("none");
            Collections.addAll(headings, pkgCategoryCodes.toArray(new String[pkgCategoryCodes.size()]));

            writer.writeNext(headings.toArray(new String[headings.size()]));

            // stream out the packages.

            PrefetchTreeNode treeNode = new PrefetchTreeNode();
            treeNode.addPath(Pkg.PKG_PKG_CATEGORIES_PROPERTY);

            long startMs = System.currentTimeMillis();
            LOGGER.info("will produce category coverage spreadsheet report");

            int count = pkgOrchestrationService.each(
                    context,
                    treeNode,
                    Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE)),
                    new Callback<Pkg>() {
                        @Override
                        public boolean process(Pkg pkg) {
                            Optional<PkgVersionLocalization> locOptional = getAnyPkgVersionLocalizationForPkg(context, pkg);

                            List<String> cols = Lists.newArrayList();
                            cols.add(pkg.getName());
                            cols.add(locOptional.isPresent() ? locOptional.get().getSummary() : "");
                            cols.add(pkg.getPkgPkgCategories().isEmpty() ? MARKER : "");

                            for (String pkgCategoryCode : pkgCategoryCodes) {
                                cols.add(pkg.getPkgPkgCategory(pkgCategoryCode).isPresent() ? MARKER : "");
                            }

                            writer.writeNext(cols.toArray(new String[cols.size()]));

                            return true;
                        }
                    });

            LOGGER.info(
                    "did produce category coverage spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);

        }

    }

}
