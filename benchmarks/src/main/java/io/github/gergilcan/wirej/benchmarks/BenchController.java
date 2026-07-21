package io.github.gergilcan.wirej.benchmarks;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;

/**
 * The WireJ side of the controller comparison: a plain interface, backed by
 * a {@code BenchControllerImpl} generated at compile time by
 * wirej-processor. See {@link HandWrittenController} for the baseline this
 * is benchmarked against.
 */
@RestController
@ServiceClass(BenchGreetingService.class)
public interface BenchController {
    @GetMapping("/bench/{id}")
    @ServiceMethod("getById")
    ResponseEntity<?> getById(@PathVariable("id") Long id);
}
