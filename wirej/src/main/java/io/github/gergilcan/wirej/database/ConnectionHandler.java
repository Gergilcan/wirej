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
}
