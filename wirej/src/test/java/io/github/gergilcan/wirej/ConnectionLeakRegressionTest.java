package io.github.gergilcan.wirej;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.gergilcan.wirej.controllers.UserController2;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.exceptions.WireJException;

/**
 * Runs against a deliberately tiny connection pool: if a failed query ever
 * left its connection open instead of releasing it (see DatabaseStatement's
 * connection lifecycle), a handful of failures would exhaust the pool and
 * every subsequent call would start timing out.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1000"
})
class ConnectionLeakRegressionTest {

    @Autowired
    private UserController2 userController2;

    @Test
    void repeatedFilterFailuresDoNotExhaustTheConnectionPool() {
        for (int i = 0; i < 5; i++) {
            assertThrows(WireJException.class, () -> userController2.getFiltered(
                    new RequestFilters("name~~~bogus~~~operator", null, "id==DESC"),
                    new RequestPagination(0, 10)));
        }

        // If any of the 5 failed attempts above had leaked its connection, the
        // 2-connection pool would already be exhausted and this call would time
        // out instead of completing.
        assertDoesNotThrow(() -> userController2.getFiltered(
                new RequestFilters(null, null, "id==DESC"), new RequestPagination(0, 10)));
    }
}
