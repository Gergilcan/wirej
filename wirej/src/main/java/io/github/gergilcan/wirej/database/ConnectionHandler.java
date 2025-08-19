package io.github.gergilcan.wirej.database;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Component
public class ConnectionHandler {
	private final DataSource dataSource;
	private Connection connection;
	private Statement statement;
	@Setter
	private String cleanupQuery;
	@Setter
	private boolean isTest;

	public ConnectionHandler(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public Connection getConnection() throws SQLException {
		return dataSource.getConnection();
	}

	public void cleanup() throws SQLException {
		// Lazy initialization of connection and statement
		if (connection == null || connection.isClosed()) {
			connection = getConnection();
			statement = connection.createStatement();
		}

		if (!isTest || cleanupQuery == null || cleanupQuery.isEmpty()) {
			return;
		}

		// Execute cleanup
		statement.execute(cleanupQuery);
	}
}
