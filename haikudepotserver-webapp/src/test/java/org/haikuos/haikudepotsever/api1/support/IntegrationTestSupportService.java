/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.api1.support;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.PkgService;
import org.haikuos.haikudepotserver.support.Closeables;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.InputStream;

/**
 * <p>This class is designed to help out with creating some common test data that can be re-used between tests.</p>
 */

@Service
public class IntegrationTestSupportService {

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PkgService pkgService;

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

    private void addPkgIcon(ObjectContext objectContext, Pkg pkg, int size) {
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream(String.format("/sample-%dx%d.png",size,size));
            pkgService.storePkgIconImage(inputStream, size, objectContext, pkg);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading an icon",e);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    private void addPkgIcons(ObjectContext objectContext, Pkg pkg) {
        addPkgIcon(objectContext, pkg, 16);
        addPkgIcon(objectContext, pkg, 32);
    }

    public StandardTestData createStandardTestData() {

        ObjectContext context = getObjectContext();
        StandardTestData result = new StandardTestData();

        Architecture x86 = Architecture.getByCode(context, "x86").get();

        result.repository = context.newObject(Repository.class);
        result.repository.setActive(Boolean.TRUE);
        result.repository.setCode("testrepository");
        result.repository.setArchitecture(x86);
        result.repository.setUrl("file:///");

        result.pkg1 = context.newObject(Pkg.class);
        result.pkg1.setActive(true);
        result.pkg1.setName("pkg1");

        addPkgScreenshot(context,result.pkg1);
        addPkgScreenshot(context,result.pkg1);
        addPkgScreenshot(context,result.pkg1);
        addPkgIcons(context, result.pkg1);

        result.pkg1Version1 = context.newObject(PkgVersion.class);
        result.pkg1Version1.setActive(Boolean.FALSE);
        result.pkg1Version1.setArchitecture(x86);
        result.pkg1Version1.setMajor("1");
        result.pkg1Version1.setMicro("2");
        result.pkg1Version1.setRevision(3);
        result.pkg1Version1.setPkg(result.pkg1);
        result.pkg1Version1.setRepository(result.repository);

        result.pkg1Version2 = context.newObject(PkgVersion.class);
        result.pkg1Version2.setActive(Boolean.TRUE);
        result.pkg1Version2.setArchitecture(x86);
        result.pkg1Version2.setMajor("1");
        result.pkg1Version2.setMicro("2");
        result.pkg1Version2.setRevision(4);
        result.pkg1Version2.setPkg(result.pkg1);
        result.pkg1Version2.setRepository(result.repository);

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

        return result;
    }

    /**
     * <p>This class is a container that carries some basic test-case data.</p>
     */

    public static class StandardTestData {

        public Repository repository;

        public Pkg pkg1;
        public PkgVersion pkg1Version1;
        public PkgVersion pkg1Version2;

        public Pkg pkg2;
        public PkgVersion pkg2Version1;

        public Pkg pkg3;
        public PkgVersion pkg3Version1;
    }

}
