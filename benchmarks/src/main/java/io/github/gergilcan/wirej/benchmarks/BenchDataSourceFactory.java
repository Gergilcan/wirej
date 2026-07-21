package io.github.gergilcan.wirej.benchmarks;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class BenchDataSourceFactory {
    private BenchDataSourceFactory() {
    }

    static DataSource createSeeded() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:bench;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS bench_users (id BIGINT PRIMARY KEY, name VARCHAR(255))");
        jdbcTemplate.update("MERGE INTO bench_users (id, name) VALUES (?, ?)", 1L, "Benchmark User");
        return dataSource;
    }
}
