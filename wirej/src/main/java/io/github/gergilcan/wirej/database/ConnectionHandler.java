package io.github.gergilcan.wirej.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionHandler {
	private final DataSource dataSource;

	public Connection getConnection() {
		var connection = DataSourceUtils.getConnection(dataSource);
		log.debug("Connection obtained: {}", connection);
		return connection;
	}

	/**
	 * Releases a connection obtained from {@link #getConnection()}. Uses
	 * {@link DataSourceUtils#releaseConnection} so that a connection bound to an
	 * active Spring-managed transaction is left open for the transaction, and a
	 * standalone connection is returned to the pool. Calling
	 * {@code connection.close()} directly would close a transaction's connection
	 * prematurely.
	 */
	public void releaseConnection(Connection connection) {
		DataSourceUtils.releaseConnection(connection, dataSource);
	}
}
