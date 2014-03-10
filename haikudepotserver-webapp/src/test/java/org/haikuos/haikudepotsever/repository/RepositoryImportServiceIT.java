/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.repository;

import com.google.common.base.Optional;
import com.google.common.io.Files;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.pkg.model.PkgRepositoryImportJob;
import org.haikuos.haikudepotserver.repository.RepositoryImportService;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.junit.Test;

import javax.annotation.Resource;
import java.io.File;

/**
 * <p>This test will load in a fake repository HPKR file and will then check to see that it imported correctly.</p>
 */

public class RepositoryImportServiceIT extends AbstractIntegrationTest {

    public final static long DELAY_PROCESSSUBMITTEDTESTJOB = 60 * 1000; // 60s

    @Resource
    RepositoryImportService repositoryImportService;

    private void verifyPackage(
            ObjectContext context,
            String name) {
        Optional<Pkg> pkgOptional = Pkg.getByName(context, name);
        Assertions.assertThat(pkgOptional.isPresent()).isTrue();
    }

    @Test
    public void testImportThenCheck() throws Exception {

        File temporaryFile = null;

        try {
            temporaryFile = File.createTempFile("haikudepotserver-test-",".hpkr");

            // get the test hpkr data and copy it into a temporary file that can be used as a source
            // for a repository.

            Files.write(getResourceData("/sample-repo.hpkr"), temporaryFile);

            // first setup a fake repository to import that points at the local test HPKR file.

            {
                ObjectContext context = serverRuntime.getContext();
                Repository repository = context.newObject(Repository.class);
                repository.setActive(Boolean.TRUE);
                repository.setCode("test");
                repository.setUrl("file://" + temporaryFile.getAbsolutePath());
                repository.setArchitecture(Architecture.getByCode(context, "x86").get());
                context.commitChanges();
            }

            // do the import.

            repositoryImportService.submit(new PkgRepositoryImportJob("test"));

            // wait for it to finish.

            {
                long startMs = System.currentTimeMillis();
                while(
                        repositoryImportService.isProcessingSubmittedJobs()
                                && (System.currentTimeMillis() - startMs) < DELAY_PROCESSSUBMITTEDTESTJOB);

                if(repositoryImportService.isProcessingSubmittedJobs()) {
                    throw new IllegalStateException("test processing of the sample repo has taken > "+DELAY_PROCESSSUBMITTEDTESTJOB+"ms");
                }
            }

            // now pull out some known packages and make sure they are imported correctly.
            // TODO - this is a fairly simplistic test; do some more checks.

            {
                ObjectContext context = serverRuntime.getContext();
                verifyPackage(context,"apr");
                verifyPackage(context,"zlib_x86_gcc2_devel");
            }
        }
        finally {
            if(null!=temporaryFile) {
                temporaryFile.delete();
            }
        }
    }

}
