/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.commons.collections4.CollectionUtils;
import org.haiku.haikudepotserver.dataobjects.JobData;
import org.haiku.haikudepotserver.storage.model.DataStorageInUseChecker;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>For the database storage of jobs, detects those datas which belong to
 * the job system.</p>
 */

@Component
public class DbJobDataStorageInUseChecker implements DataStorageInUseChecker {

    private final ServerRuntime serverRuntime;

    public DbJobDataStorageInUseChecker(ServerRuntime serverRuntime) {
        this.serverRuntime = serverRuntime;
    }

    @Override
    public Set<String> inUse(Collection<String> codes) {
        if (CollectionUtils.isEmpty(codes)) {
            return Set.of();
        }

        ObjectContext context = serverRuntime.newContext();

        List<Object[]> rows = ObjectSelect.query(org.haiku.haikudepotserver.dataobjects.JobData.class)
                .where(JobData.STORAGE_CODE.in(codes))
                .fetchDataRows()
                .columns(JobData.STORAGE_CODE)
                .select(context);

        return rows.stream().map(r -> r[0].toString()).collect(Collectors.toSet());
    }
}
