package io.github.gergilcan.wirej.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Guards the fix for releaseConnection: it must go through DataSourceUtils
 * rather than Connection#close() directly, so a connection participating in
 * an ongoing Spring-managed transaction isn't physically closed out from
 * under that transaction.
 */
class ConnectionHandlerTest {

    private DataSource dataSource;
    private ConnectionHandler connectionHandler;

    @BeforeEach
    void setUp() {
        var h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:connection-handler-test;DB_CLOSE_DELAY=-1");
        dataSource = h2DataSource;
        connectionHandler = new ConnectionHandler(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.hasResource(dataSource)) {
            TransactionSynchronizationManager.unbindResource(dataSource);
        }
    }

    @Test
    void releaseConnectionClosesItWhenNoTransactionIsActive() throws SQLException {
        Connection connection = connectionHandler.getConnection();
        assertThat(connection.isClosed()).isFalse();

        connectionHandler.releaseConnection(connection);

        assertThat(connection.isClosed()).isTrue();
    }

    @Test
    void releaseConnectionDoesNotCloseAConnectionBoundToTheCurrentTransaction() throws SQLException {
        Connection transactionalConnection = DataSourceUtils.getConnection(dataSource);
        ConnectionHolder holder = new ConnectionHolder(transactionalConnection);
        holder.setSynchronizedWithTransaction(true);
        TransactionSynchronizationManager.bindResource(dataSource, holder);

        try {
            Connection obtained = connectionHandler.getConnection();
            assertThat(obtained).isSameAs(transactionalConnection);

            connectionHandler.releaseConnection(obtained);

            assertThat(obtained.isClosed())
                    .as("a connection bound to the current transaction must not be closed by releaseConnection")
                    .isFalse();
        } finally {
            TransactionSynchronizationManager.unbindResource(dataSource);
            transactionalConnection.close();
        }
    }
}
