/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogResult;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterRequest;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterResult;
import org.haiku.haikudepotserver.api2.PkgApiService;
import org.haiku.haikudepotserver.api2.model.GetPkgChangelogRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.IncrementViewCounterRequestEnvelope;
import org.springframework.stereotype.Component;

/**
 * <p>See {@link PkgApi} for details on the methods this API affords.</p>
 */

@Deprecated
@Component("pkgApiImplV1")
public class PkgApiImpl implements PkgApi {

    private final PkgApiService pkgApiService;

    public PkgApiImpl(PkgApiService pkgApiService) {
        this.pkgApiService = Preconditions.checkNotNull(pkgApiService);
    }

    @Override
    public GetPkgChangelogResult getPkgChangelog(GetPkgChangelogRequest request) {
        Preconditions.checkArgument(null!=request, "a request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "a package name must be supplied");
        org.haiku.haikudepotserver.api2.model.GetPkgChangelogResult resultV2
                = pkgApiService.getPkgChangelog(new GetPkgChangelogRequestEnvelope().pkgName(request.pkgName));
        GetPkgChangelogResult result = new GetPkgChangelogResult();
        result.content = resultV2.getContent();
        return result;
    }

    @Override
    public IncrementViewCounterResult incrementViewCounter(IncrementViewCounterRequest request) {
        Preconditions.checkArgument(null!=request, "the request object must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.name), "the package name must be supplied");

        pkgApiService.incrementViewCounter(new IncrementViewCounterRequestEnvelope()
                .architectureCode(request.architectureCode)
                .repositoryCode(request.repositoryCode)
                .repositorySourceCode(request.repositorySourceCode)
                .name(request.name)
                .major(request.major)
                .minor(request.minor)
                .micro(request.micro)
                .preRelease(request.preRelease)
                .revision(request.revision));

        return new IncrementViewCounterResult();
    }

}
