package io.github.gergilcan.wirej.benchmarks;

/**
 * A trivial in-memory service, deliberately doing no I/O, so the controller
 * benchmark measures pure dispatch overhead rather than being dominated by
 * whatever the service does.
 */
public class BenchGreetingService {
    public BenchUser getById(Long id) {
        return new BenchUser(id, "Benchmark User " + id);
    }
}
