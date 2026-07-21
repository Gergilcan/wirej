package io.github.gergilcan.wirej.benchmarks;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A plain, idiomatic Spring Boot repository doing the exact same query as
 * {@link BenchUserRepository} via {@link JdbcTemplate}, with no WireJ
 * involvement at all. This is the baseline the generated code is measured
 * against.
 */
public class HandWrittenUserRepository {
    private final JdbcTemplate jdbcTemplate;

    public HandWrittenUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BenchUser findById(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT id, name FROM bench_users WHERE id = ?",
                (rs, rowNum) -> new BenchUser(rs.getLong("id"), rs.getString("name")),
                id);
    }
}
