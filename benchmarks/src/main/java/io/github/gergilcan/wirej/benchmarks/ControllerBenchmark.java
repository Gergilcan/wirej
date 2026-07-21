package io.github.gergilcan.wirej.benchmarks;

import java.util.concurrent.TimeUnit;

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
import org.springframework.http.ResponseEntity;

/**
 * Compares a WireJ-generated controller method against a hand-written
 * controller doing the identical service call and {@link ResponseEntity}
 * wrapping, with no database or HTTP layer involved, so this isolates pure
 * dispatch overhead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class ControllerBenchmark {
    private BenchController wireJController;
    private HandWrittenController handWrittenController;

    @Setup(Level.Trial)
    public void setUp() {
        BenchGreetingService service = new BenchGreetingService();
        wireJController = new BenchControllerImpl(service);
        handWrittenController = new HandWrittenController(service);
    }

    @Benchmark
    public ResponseEntity<?> wireJGetById() {
        return wireJController.getById(1L);
    }

    @Benchmark
    public ResponseEntity<?> handWrittenGetById() {
        return handWrittenController.getById(1L);
    }
}
