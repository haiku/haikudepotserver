/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.dataobjects.MediaType;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.support.Closeables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.InputStream;

/**
 * <p>This class is designed to help out with creating some common test data that can be re-used between tests.</p>
 */

@Service
public class IntegrationTestSupportService {

    protected static Logger logger = LoggerFactory.getLogger(IntegrationTestSupportService.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PkgOrchestrationService pkgService;

    private ObjectContext objectContext = null;

    public ObjectContext getObjectContext() {
        if(null==objectContext) {
            objectContext = serverRuntime.getContext();
        }

        return objectContext;
    }

    private PkgScreenshot addPkgScreenshot(ObjectContext objectContext, Pkg pkg) {
        InputStream inputStream = null;

        try {
            inputStream = IntegrationTestSupportService.class.getResourceAsStream("/sample-320x240.png");
            return pkgService.storePkgScreenshotImage(inputStream, objectContext, pkg);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading a sample screenshot into a test package",e);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    private void addPngPkgIcon(ObjectContext objectContext, Pkg pkg, int size) {
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream(String.format("/sample-%dx%d.png",size,size));
            pkgService.storePkgIconImage(
                    inputStream,
                    MediaType.getByCode(objectContext, com.google.common.net.MediaType.PNG.toString()).get(),
                    size,
                    objectContext,
                    pkg);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading an icon",e);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    private void addHvifPkgIcon(ObjectContext objectContext, Pkg pkg) {
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream("/sample.hvif");
            pkgService.storePkgIconImage(
                    inputStream,
                    MediaType.getByCode(objectContext, MediaType.MEDIATYPE_HAIKUVECTORICONFILE).get(),
                    null,
                    objectContext,
                    pkg);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading an icon",e);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    private void addPkgIcons(ObjectContext objectContext, Pkg pkg) {
        addPngPkgIcon(objectContext, pkg, 16);
        addPngPkgIcon(objectContext, pkg, 32);
        addHvifPkgIcon(objectContext, pkg);
    }

    public StandardTestData createStandardTestData() {

        logger.info("will create standard test data");

        ObjectContext context = getObjectContext();
        StandardTestData result = new StandardTestData();

        Architecture x86 = Architecture.getByCode(context, "x86").get();
        Architecture x86_gcc2 = Architecture.getByCode(context, "x86_gcc2").get();

        result.repository = context.newObject(Repository.class);
        result.repository.setActive(Boolean.TRUE);
        result.repository.setCode("testrepository");
        result.repository.setArchitecture(x86);
        result.repository.setUrl("file:///");

        result.pkg1 = context.newObject(Pkg.class);
        result.pkg1.setActive(true);
        result.pkg1.setName("pkg1");

        {
            PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
            result.pkg1.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
            pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "GRAPHICS").get());
        }

        addPkgScreenshot(context,result.pkg1);
        addPkgScreenshot(context,result.pkg1);
        addPkgScreenshot(context,result.pkg1);
        addPkgIcons(context, result.pkg1);

        result.pkg1Version1x86 = context.newObject(PkgVersion.class);
        result.pkg1Version1x86.setActive(Boolean.FALSE);
        result.pkg1Version1x86.setArchitecture(x86);
        result.pkg1Version1x86.setMajor("1");
        result.pkg1Version1x86.setMicro("2");
        result.pkg1Version1x86.setRevision(3);
        result.pkg1Version1x86.setPkg(result.pkg1);
        result.pkg1Version1x86.setRepository(result.repository);

        result.pkg1Version2x86 = context.newObject(PkgVersion.class);
        result.pkg1Version2x86.setActive(Boolean.TRUE);
        result.pkg1Version2x86.setArchitecture(x86);
        result.pkg1Version2x86.setMajor("1");
        result.pkg1Version2x86.setMicro("2");
        result.pkg1Version2x86.setRevision(4);
        result.pkg1Version2x86.setPkg(result.pkg1);
        result.pkg1Version2x86.setRepository(result.repository);

        {
            PkgVersionLocalization pkgVersionLocalization = context.newObject(PkgVersionLocalization.class);
            pkgVersionLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH).get());
            pkgVersionLocalization.setDescription("pkg1Version2DescriptionEnglish");
            pkgVersionLocalization.setSummary("pkg1Version2SummaryEnglish");
            result.pkg1Version2x86.addToManyTarget(PkgVersion.PKG_VERSION_LOCALIZATIONS_PROPERTY, pkgVersionLocalization, true);
        }

        {
            PkgVersionLocalization pkgVersionLocalization = context.newObject(PkgVersionLocalization.class);
            pkgVersionLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH).get());
            pkgVersionLocalization.setDescription("pkg1Version2DescriptionSpanish");
            pkgVersionLocalization.setSummary("pkg1Version2SummarySpanish");
            result.pkg1Version2x86.addToManyTarget(PkgVersion.PKG_VERSION_LOCALIZATIONS_PROPERTY, pkgVersionLocalization, true);
        }

        result.pkg1Version2x86_gcc2 = context.newObject(PkgVersion.class);
        result.pkg1Version2x86_gcc2.setActive(Boolean.TRUE);
        result.pkg1Version2x86_gcc2.setArchitecture(x86_gcc2);
        result.pkg1Version2x86_gcc2.setMajor("1");
        result.pkg1Version2x86_gcc2.setMicro("2");
        result.pkg1Version2x86_gcc2.setRevision(4);
        result.pkg1Version2x86_gcc2.setPkg(result.pkg1);
        result.pkg1Version2x86_gcc2.setRepository(result.repository);

        // this is the same as the x86 version so that comparisons with English will happen.

        {
            PkgVersionLocalization pkgVersionLocalization = context.newObject(PkgVersionLocalization.class);
            pkgVersionLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH).get());
            pkgVersionLocalization.setDescription("pkg1Version2DescriptionEnglish");
            pkgVersionLocalization.setSummary("pkg1Version2SummaryEnglish");
            result.pkg1Version2x86_gcc2.addToManyTarget(PkgVersion.PKG_VERSION_LOCALIZATIONS_PROPERTY, pkgVersionLocalization, true);
        }

        result.pkg2 = context.newObject(Pkg.class);
        result.pkg2.setActive(true);
        result.pkg2.setName("pkg2");

        result.pkg2Version1 = context.newObject(PkgVersion.class);
        result.pkg2Version1.setActive(Boolean.TRUE);
        result.pkg2Version1.setArchitecture(x86);
        result.pkg2Version1.setMajor("1");
        result.pkg2Version1.setMicro("2");
        result.pkg2Version1.setRevision(3);
        result.pkg2Version1.setPkg(result.pkg2);
        result.pkg2Version1.setRepository(result.repository);

        result.pkg3 = context.newObject(Pkg.class);
        result.pkg3.setActive(true);
        result.pkg3.setName("pkg3");

        result.pkg3Version1 = context.newObject(PkgVersion.class);
        result.pkg3Version1.setActive(Boolean.TRUE);
        result.pkg3Version1.setArchitecture(x86);
        result.pkg3Version1.setMajor("1");
        result.pkg3Version1.setMicro("2");
        result.pkg3Version1.setRevision(3);
        result.pkg3Version1.setPkg(result.pkg3);
        result.pkg3Version1.setRepository(result.repository);

        context.commitChanges();

        logger.info("did create standard test data");

        return result;
    }

    /**
     * <p>This class is a container that carries some basic test-case data.</p>
     */

    public static class StandardTestData {

        public Repository repository;

        public Pkg pkg1;
        public PkgVersion pkg1Version1x86;
        public PkgVersion pkg1Version2x86;
        public PkgVersion pkg1Version2x86_gcc2;

        public Pkg pkg2;
        public PkgVersion pkg2Version1;

        public Pkg pkg3;
        public PkgVersion pkg3Version1;
    }

}
