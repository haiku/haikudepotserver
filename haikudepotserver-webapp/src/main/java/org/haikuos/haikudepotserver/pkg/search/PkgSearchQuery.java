/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.cayenne.CayenneException;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.access.OperationObserver;
import org.apache.cayenne.access.ResultIterator;
import org.apache.cayenne.query.AbstractQuery;
import org.apache.cayenne.query.SQLAction;
import org.apache.cayenne.query.SQLActionVisitor;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * <p>This query is used to search for package versions based on some criteria that are provided
 * in a {@link org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification} object.
 * Ultimately it will use raw SQL to perform the search.</p>
 */

// [apl]
// I think this could do with a bit more work to fit better into the Cayenne
// infrastructure.  See notes below.

public class PkgSearchQuery extends AbstractQuery {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgSearchQuery.class);

    private PkgSearchSpecification searchSpecification;

    public PkgSearchQuery(PkgSearchSpecification searchSpecification) {
        Preconditions.checkArgument(null!=searchSpecification, "the search specification is required");
        this.searchSpecification = searchSpecification;
        setRoot(PkgVersion.class);
    }

    public PkgSearchSpecification getSearchSpecification() {
        return searchSpecification;
    }

    public void setSearchSpecification(PkgSearchSpecification searchSpecification) {
        this.searchSpecification = searchSpecification;
    }

    @Override
    public SQLAction createSQLAction(final SQLActionVisitor visitor) {

        return new SQLAction() {

            // This seems bad, it must be possible to get access to the meta data from
            // here and create the data from that meta data somehow?

            public DataRow generateDataRow(ResultSet rs) throws SQLException {
                Object root = PkgSearchQuery.this.getRoot();
                Map<String,Object> cols = Maps.newHashMap();

                if(root.equals(PkgVersion.class)) {
                    cols.put("active", rs.getBoolean("active"));
                    cols.put("architecture_id", rs.getLong("architecture_id"));
                    cols.put("create_timestamp", rs.getTimestamp("create_timestamp"));
                    cols.put("id", rs.getLong("id"));
                    cols.put("major", rs.getString("major"));
                    cols.put("minor", rs.getString("minor")); // NULLABLE
                    cols.put("micro", rs.getString("micro")); // NULLABLE
                    cols.put("modify_timestamp", rs.getTimestamp("modify_timestamp"));
                    cols.put("pkg_id", rs.getLong("pkg_id"));
                    cols.put("pre_release", rs.getString("pre_release")); // NULLABLE
                    cols.put("repository_id", rs.getLong("repository_id"));
                    cols.put("revision", rs.getObject("revision"));
                    cols.put("view_counter", rs.getLong("view_counter"));
                    cols.put("is_latest", rs.getBoolean("is_latest"));
                    cols.put("payload_length", rs.getObject("payload_length")); // NULLABLE
                }
                else {
                    throw new IllegalStateException("unrecognized root; " + root.toString());
                }

                return new DataRow(cols);
            }

            public void appendSqlSearchSelectClause(StringBuilder sql) {
                Object root = PkgSearchQuery.this.getRoot();

                if(root.equals(PkgVersion.class)) {
                    sql.append("pv.*\n");
                }
                else {
                    throw new IllegalStateException("unrecognized root; " + root.toString());
                }
            }

            @Override
            public void performAction(Connection connection, OperationObserver observer) throws Exception {

                StringBuilder sql = new StringBuilder();
                List<Object> parameterAccumulator = Lists.newArrayList();
                sql.append("SELECT\n");
                appendSqlSearchSelectClause(sql);
                sql.append("FROM\n");
                PkgSearchHelper.appendSqlSearchFromClause(sql, parameterAccumulator, searchSpecification);
                sql.append("WHERE\n");
                PkgSearchHelper.appendSqlSearchWhereClause(sql, parameterAccumulator, searchSpecification);
                sql.append("ORDER BY\n");
                PkgSearchHelper.appendSearchOrderClause(sql, searchSpecification);
                sql.append("OFFSET ?\n");
                sql.append("LIMIT ?\n");
                parameterAccumulator.add(searchSpecification.getOffset());
                parameterAccumulator.add(searchSpecification.getLimit());

                LOGGER.debug("pkg search query; {}", sql.toString());

                try(PreparedStatement statement = connection.prepareStatement(sql.toString())) {

                    for(int i=0;i<parameterAccumulator.size();i++) {
                        statement.setObject(i+1,parameterAccumulator.get(i));
                    }

                    try(final ResultSet resultSet = statement.executeQuery()) {

                        // I think it would be easier to use a JDBCResultIterator here, but it is not
                        // entirely clear how I would get all of the required objects together to
                        // make one of these.  It seems that SQLActionVisitor is very tuned towards
                        // some fixed supported cases and doesn't allow for this sort of generalization?

                        ResultIterator resultIterator = new ResultIterator() {

                            private Boolean hasNextRow = null;

                            @Override
                            public List<?> allRows() throws CayenneException {
                                Object row;
                                List<Object> result = Lists.newArrayList();

                                while(null != (row = nextRow())) {
                                    result.add(row);
                                }

                                return result;
                            }

                            @Override
                            public boolean hasNextRow() throws CayenneException {
                                if(null==hasNextRow) {
                                    try {
                                        hasNextRow = resultSet.next();
                                    }
                                    catch(SQLException se) {
                                        throw new CayenneException("unable to see if there is a next row", se);
                                    }
                                }

                                return hasNextRow;
                            }

                            @Override
                            public Object nextRow() throws CayenneException {
                                try {
                                    return hasNextRow() ? generateDataRow(resultSet) : null;
                                }
                                catch(SQLException se) {
                                    throw new CayenneException("unable to get the next row", se);
                                }
                                finally {
                                    hasNextRow = null;
                                }
                            }

                            @Override
                            public void skipRow() throws CayenneException {
                                hasNextRow = null;
                            }

                            @Override
                            public void close() throws CayenneException {
                                // nothing to do here because the result set will be closed by the enclosing
                                // try with resources
                            }
                        };

                        if (!observer.isIteratedResult()) {
                            observer.nextRows(PkgSearchQuery.this, resultIterator.allRows());
                        }
                        else {
                            observer.nextRows(PkgSearchQuery.this, resultIterator);
                        }

                    }

                }

            }

        };

    }
}
