package io.github.gergilcan.wirej;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.gergilcan.wirej.controllers.UserController2;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.exceptions.WireJException;
import io.github.gergilcan.wirej.repositories.UserRepository;

/**
 * Guards against java.lang.reflect.Proxy silently swallowing checked
 * exceptions into a message-less UndeclaredThrowableException: repository
 * and controller proxy methods don't declare "throws SQLException", so any
 * checked exception thrown across them must be surfaced as an unchecked
 * WireJException instead, with the original cause and useful context intact.
 */
@SpringBootTest(classes = TestApplication.class)
class ExceptionPropagationTest {

    @Autowired
    private UserController2 userController2;

    @Autowired
    private UserRepository userRepository;

    @Test
    void sqlErrorSurfacesAsWireJExceptionThroughRepositoryProxyNotUndeclaredThrowable() {
        User user = new User();
        user.setId(501L);
        user.setName("Dup User");
        userRepository.create(user);

        WireJException ex = assertThrows(WireJException.class, () -> userRepository.create(user));

        assertThat(ex).isNotInstanceOf(UndeclaredThrowableException.class);
        assertThat(ex.getMessage()).contains("create").contains("User");
        assertThat(ex.getCause()).isInstanceOf(SQLException.class);
    }

    @Test
    void sqlErrorSurfacesAsWireJExceptionThroughControllerProxyNotDoubleWrapped() {
        User user = new User();
        user.setId(502L);
        user.setName("Dup User 2");
        userController2.createUser(user);

        Throwable ex = assertThrows(Throwable.class, () -> userController2.createUser(user));

        assertThat(ex).isNotInstanceOf(UndeclaredThrowableException.class);
        assertThat(ex).isInstanceOf(WireJException.class);
    }

    @Test
    void malformedRsqlFilterThrowsWireJExceptionInsteadOfSilentlyDroppingClause() {
        WireJException ex = assertThrows(WireJException.class,
                () -> userController2.getFiltered(
                        new RequestFilters("name~~~bogus~~~operator", null, "id==DESC"),
                        new RequestPagination(0, 10)));

        assertThat(ex.getMessage()).contains("Unrecognized filter clause");
    }
}
