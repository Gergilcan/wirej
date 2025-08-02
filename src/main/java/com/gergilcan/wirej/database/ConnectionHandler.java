package com.gergilcan.wirej.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
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
		try (var stream = this.getClass().getClassLoader().getResourceAsStream("fixtures/cleanup.sql")) {
			if (stream != null) {
				cleanupQuery = new String(stream.readAllBytes());
				// Get a connection but don't create statement yet - we'll do that on first use
				// to avoid holding an idle connection and statement
			}
		} catch (IOException e) {
			log.error("Failed to load cleanup SQL: {}", e.getMessage(), e);
		}
	}

	public Connection getGladstone2Connection() throws SQLException {
		return dataSource.getConnection();
	}

	public void cleanup() throws SQLException {
		if (!isTestDatabase()) {
			return;
		}

		// Lazy initialization of connection and statement
		if (connection == null || connection.isClosed()) {
			connection = getGladstone2Connection();
			statement = connection.createStatement();
		}

		// Execute cleanup
		statement.execute(cleanupQuery);
	}

	private boolean isTestDatabase() {
		return System.getenv("GLADSTONE2_DATABASE_URL").equals("postgres")
				|| System.getenv("GLADSTONE2_DATABASE_URL").equals("localhost");
	}
}
