package io.github.gergilcan.wirej.benchmarks;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A plain, hand-written controller doing the exact same delegation as
 * {@link BenchController}'s generated implementation, with no WireJ
 * involvement at all. This is the baseline the generated code is measured
 * against.
 */
public class HandWrittenController {
    private final BenchGreetingService service;

    public HandWrittenController(BenchGreetingService service) {
        this.service = service;
    }

    public ResponseEntity<?> getById(Long id) {
        BenchUser result = service.getById(id);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
