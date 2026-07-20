package io.github.gergilcan.wirej;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.repositories.UserRepository;

@SpringBootTest(classes = TestApplication.class)
class QueryOperationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void autoInferenceMisroutesAMisleadingMethodNameToAScalarRead() {
        User user = new User();
        user.setId(701L);
        user.setName("Auto Bug User");
        userRepository.create(user);

        assertThrows(ClassCastException.class, () -> userRepository.retrieveAccountsSummaryAuto(701L));
    }

    @Test
    void explicitSelectOperationAvoidsTheNameBasedMisrouting() {
        User user = new User();
        user.setId(702L);
        user.setName("Explicit Select User");
        userRepository.create(user);

        User result = userRepository.retrieveAccountsSummary(702L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(702L);
        assertThat(result.getName()).isEqualTo("Explicit Select User");
    }

    @Test
    void explicitCountOperationWorksRegardlessOfMethodName() {
        Long count = userRepository.tallyUsers(new RequestFilters(null, null, null));

        assertThat(count).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void classParameterIsSkippedRatherThanReflectedIntoRegardlessOfMethodName() {
        Long count = userRepository.tallyUsersWithEntityClass(new RequestFilters(null, null, null), User.class);

        assertThat(count).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(0L);
    }
}
