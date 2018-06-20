/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.proxy.backend.common;

import com.sun.rowset.CachedRowSetImpl;
import io.shardingsphere.core.parsing.parser.sql.SQLStatement;
import io.shardingsphere.core.parsing.parser.sql.dml.insert.InsertStatement;
import io.shardingsphere.proxy.transport.mysql.constant.StatusFlag;
import io.shardingsphere.proxy.transport.mysql.packet.command.CommandResponsePackets;
import io.shardingsphere.proxy.transport.mysql.packet.generic.OKPacket;

import javax.sql.rowset.CachedRowSet;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;

/**
 * Statement execute worker.
 *
 * @author zhangyonglun
 * @author zhaojun
 */
public final class StatementExecuteWorker extends ExecuteWorker implements Callable<CommandResponsePackets> {
    
    private static final Integer FETCH_ONE_ROW_A_TIME = Integer.MIN_VALUE;
    
    private final PreparedStatement preparedStatement;
    
    public StatementExecuteWorker(final StatementExecuteBackendHandler statementExecuteBackendHandler, final SQLStatement sqlStatement, final PreparedStatement preparedStatement) {
        super(statementExecuteBackendHandler, sqlStatement);
        this.preparedStatement = preparedStatement;
    }
    
    @Override
    protected CommandResponsePackets executeQueryWithStreamResultSet() throws SQLException {
        preparedStatement.setFetchSize(FETCH_ONE_ROW_A_TIME);
        setJDBCPreparedStatementParameters(preparedStatement);
        getExecuteBackendHandler().getJdbcResource().getResultSets().add(preparedStatement.executeQuery());
        return getQueryDatabaseProtocolPackets();
    }
    
    @Override
    protected CommandResponsePackets executeQueryWithNonStreamResultSet() throws SQLException {
        setJDBCPreparedStatementParameters(preparedStatement);
        ResultSet resultSet = preparedStatement.executeQuery();
        CachedRowSet cachedRowSet = new CachedRowSetImpl();
        cachedRowSet.populate(resultSet);
        getExecuteBackendHandler().getJdbcResource().getResultSets().add(cachedRowSet);
        return getQueryDatabaseProtocolPackets();
    }
    
    @Override
    protected CommandResponsePackets executeUpdate() throws SQLException {
        PreparedStatement preparedStatement = null;
        int affectedRows;
        long lastInsertId = 0;
        setJDBCPreparedStatementParameters(preparedStatement);
        if (getSqlStatement() instanceof InsertStatement) {
            affectedRows = preparedStatement.executeUpdate();
            lastInsertId = getGeneratedKey(preparedStatement);
        } else {
            affectedRows = preparedStatement.executeUpdate();
        }
        return new CommandResponsePackets(new OKPacket(1, affectedRows, lastInsertId, StatusFlag.SERVER_STATUS_AUTOCOMMIT.getValue(), 0, ""));
    }
    
    @Override
    protected CommandResponsePackets executeCommon() throws SQLException {
        setJDBCPreparedStatementParameters(preparedStatement);
        boolean hasResultSet = preparedStatement.execute();
        if (hasResultSet) {
            return getCommonDatabaseProtocolPackets(preparedStatement.getResultSet());
        } else {
            return new CommandResponsePackets(new OKPacket(1, preparedStatement.getUpdateCount(), 0, StatusFlag.SERVER_STATUS_AUTOCOMMIT.getValue(), 0, ""));
        }
    }
    
    private void setJDBCPreparedStatementParameters(final PreparedStatement preparedStatement) throws SQLException {
        StatementExecuteBackendHandler statementExecuteBackendHandler = (StatementExecuteBackendHandler) getExecuteBackendHandler();
        for (int i = 0; i < statementExecuteBackendHandler.getComStmtExecuteParameters().size(); i++) {
            preparedStatement.setObject(i + 1, statementExecuteBackendHandler.getComStmtExecuteParameters().get(i));
        }
    }
}
