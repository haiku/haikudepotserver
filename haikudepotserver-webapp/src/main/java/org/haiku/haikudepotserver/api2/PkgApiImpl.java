/*
 * Copyright 2021-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogResult;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterRequest;
import org.haiku.haikudepotserver.api2.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;

@Controller
public class PkgApiImpl extends AbstractApiImpl implements  PkgApi {

    private final org.haiku.haikudepotserver.api1.PkgApi pkgApiV1;

    public PkgApiImpl(org.haiku.haikudepotserver.api1.PkgApi pkgApiV1) {
        this.pkgApiV1 = pkgApiV1;
    }

    @Override
    public ResponseEntity<IncrementViewCounterResponseEnvelope> incrementViewCounter(@Valid IncrementViewCounterRequestEnvelope incrementViewCounterRequestEnvelope) {
        IncrementViewCounterRequest requestV1 = new IncrementViewCounterRequest();
        requestV1.architectureCode = incrementViewCounterRequestEnvelope.getArchitectureCode();
        requestV1.repositoryCode = incrementViewCounterRequestEnvelope.getRepositoryCode();
        requestV1.repositorySourceCode = incrementViewCounterRequestEnvelope.getRepositorySourceCode();
        requestV1.name = incrementViewCounterRequestEnvelope.getName();
        requestV1.major = incrementViewCounterRequestEnvelope.getMajor();
        requestV1.minor = incrementViewCounterRequestEnvelope.getMinor();
        requestV1.micro = incrementViewCounterRequestEnvelope.getMicro();
        requestV1.preRelease = incrementViewCounterRequestEnvelope.getPreRelease();
        requestV1.revision = incrementViewCounterRequestEnvelope.getRevision();
        pkgApiV1.incrementViewCounter(requestV1);
        return ResponseEntity.ok(new IncrementViewCounterResponseEnvelope());
    }

    @Override
    public ResponseEntity<GetPkgChangeLogResponseEnvelope> getPkgChangeLog(GetPkgChangeLogRequestEnvelope getPkgChangeLogRequestEnvelope) {
        GetPkgChangelogRequest requestV1 = new GetPkgChangelogRequest();
        requestV1.pkgName = getPkgChangeLogRequestEnvelope.getPkgName();
        GetPkgChangelogResult resultV1 = pkgApiV1.getPkgChangelog(requestV1);
        return ResponseEntity.ok(
                new GetPkgChangeLogResponseEnvelope().result(
                        new GetPkgChangeLogResult().content(resultV1.content)));
    }

}
