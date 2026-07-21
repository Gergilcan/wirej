# WireJ Benchmarks

JMH microbenchmarks comparing WireJ-generated dispatch against hand-written
Spring Boot equivalents doing the identical work. Internal tool, never
published to Maven Central (see the `pom.xml` skip flags).

## What's compared

- **`ControllerBenchmark`**: a WireJ-generated controller method
  (`BenchControllerImpl.getById`, calling `service.getById(id)` and wrapping
  the result in `ResponseEntity`) vs. `HandWrittenController` doing the
  identical two lines directly. No database or HTTP layer involved — this
  isolates pure dispatch overhead.
- **`RepositoryBenchmark`**: a WireJ-generated repository method
  (`BenchUserRepositoryImpl.findById`, going through `DatabaseStatement`)
  vs. `HandWrittenUserRepository` running the identical query through a
  plain `JdbcTemplate`, against the same in-memory H2 instance.

## Running

```bash
mvn -pl benchmarks -am install -DskipTests
java -jar benchmarks/target/benchmarks.jar
```

Add `-rf json -rff /path/to/results.json` to also write machine-readable
results. Each fork takes ~10-15s; the full suite (2 classes x 2 methods x 2
forks) takes ~1.5 minutes.

## Interpreting results

If you only change benchmark code, `mvn -pl benchmarks install` is enough —
it doesn't need `-am` (it will pick up whatever `wirej`/`wirej-processor`
jars are already installed in `~/.m2`). Use `-am` after changing anything in
`wirej` or `processor` so those get rebuilt and reinstalled first.
