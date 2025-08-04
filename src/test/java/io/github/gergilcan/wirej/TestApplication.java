package io.github.gergilcan.wirej;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

// This is the main configuration for your test environment
@SpringBootApplication
// We need to tell Spring where to find JPA entities for the test
@EntityScan(basePackages = "io.github.gergilcan.wirej.entities")
public class TestApplication {
}