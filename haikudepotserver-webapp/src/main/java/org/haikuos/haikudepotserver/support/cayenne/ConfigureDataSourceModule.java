/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.DataNodeDescriptor;
import org.apache.cayenne.configuration.server.DataSourceFactory;
import org.apache.cayenne.di.Binder;
import org.apache.cayenne.di.Module;

import javax.sql.DataSource;

/**
 * <p>This object exists to get the data source injected into it and then to pass that onto the Cayenne environment.
 * </p>
 */

public class ConfigureDataSourceModule implements Module {

    private DataSource dataSource;

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void configure(Binder binder) {
        Preconditions.checkNotNull(dataSource);
        binder.bind(DataSourceFactory.class).toInstance(new FixedDataSourceFactory(dataSource));
    }

    public static class FixedDataSourceFactory implements DataSourceFactory {

        private DataSource dataSource;

        public FixedDataSourceFactory(DataSource dataSource) {
            Preconditions.checkNotNull(dataSource);
            this.dataSource = dataSource;
        }

        @Override
        public javax.sql.DataSource getDataSource(DataNodeDescriptor dataNodeDescriptor) {
            return dataSource;
        }

    }

}
