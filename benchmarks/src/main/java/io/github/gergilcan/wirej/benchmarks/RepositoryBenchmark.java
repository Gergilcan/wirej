package io.github.gergilcan.wirej.benchmarks;

import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.gergilcan.wirej.database.ConnectionHandler;
import io.github.gergilcan.wirej.rsql.RsqlParser;

/**
 * Compares a WireJ-generated repository method against a hand-written
 * {@link JdbcTemplate}-based repository running the identical query against
 * the same H2 instance, so any measured difference reflects dispatch
 * overhead rather than query cost.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class RepositoryBenchmark {
    private BenchUserRepository wireJRepository;
    private HandWrittenUserRepository handWrittenRepository;

    @Setup(Level.Trial)
    public void setUp() {
        DataSource dataSource = BenchDataSourceFactory.createSeeded();
        ConnectionHandler connectionHandler = new ConnectionHandler(dataSource);
        RsqlParser rsqlParser = new RsqlParser();

        wireJRepository = new BenchUserRepositoryImpl(connectionHandler, rsqlParser);
        handWrittenRepository = new HandWrittenUserRepository(new JdbcTemplate(dataSource));
    }

    @Benchmark
    public BenchUser wireJFindById() {
        return wireJRepository.findById(1L);
    }

    @Benchmark
    public BenchUser handWrittenFindById() {
        return handWrittenRepository.findById(1L);
    }
}
