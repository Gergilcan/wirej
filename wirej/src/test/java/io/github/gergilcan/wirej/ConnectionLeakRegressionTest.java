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

        assertDoesNotThrow(() -> userController2.getFiltered(
                new RequestFilters(null, null, "id==DESC"), new RequestPagination(0, 10)));
    }
}
