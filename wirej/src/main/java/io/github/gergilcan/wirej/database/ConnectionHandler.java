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
	 * Releases a connection obtained via {@link #getConnection()}. Uses
	 * DataSourceUtils rather than Connection#close() directly so a connection
	 * bound to an ongoing Spring-managed transaction is left open for the
	 * transaction to finish with, instead of being closed out from under it.
	 */
	public void releaseConnection(Connection connection) {
		DataSourceUtils.releaseConnection(connection, dataSource);
		log.debug("Connection released: {}", connection);
	}
}
