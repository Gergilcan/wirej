package io.github.gergilcan.wirej.benchmarks;

import org.springframework.stereotype.Repository;

import io.github.gergilcan.wirej.annotations.QueryFile;

/**
 * The WireJ side of the repository comparison: a plain interface, backed by
 * a {@code BenchUserRepositoryImpl} generated at compile time by
 * wirej-processor. See {@link HandWrittenUserRepository} for the baseline
 * this is benchmarked against.
 */
@Repository
public interface BenchUserRepository {
    @QueryFile("/queries/findById.sql")
    BenchUser findById(Long id);
}
