package io.github.gergilcan.wirej.database;

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
	private String cleanupQuery;

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

		if (!isTestDatabase(connection)) {
			return;
		}

		// Execute cleanup
		statement.execute(cleanupQuery);
	}

	private boolean isTestDatabase(Connection connection) throws SQLException {
		return connection.getMetaData().getDatabaseProductName().equals("postgres") ||
				connection.getMetaData().getDatabaseProductName().equals("localhost");
	}
}
