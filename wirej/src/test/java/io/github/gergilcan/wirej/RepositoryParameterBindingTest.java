package io.github.gergilcan.wirej;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.repositories.UserRepository;

@SpringBootTest(classes = TestApplication.class)
class RepositoryParameterBindingTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void extraPlainIntParameterBindsAndAffectsTheQuery() {
        User user = new User();
        user.setId(801L);
        user.setName("Min Age User");
        userRepository.create(user);

        User matching = userRepository.findByIdWithMinAge(801L, 5, new RequestPagination(0, 10));
        assertThat(matching).isNotNull();
        assertThat(matching.getId()).isEqualTo(801L);

        User nonMatching = userRepository.findByIdWithMinAge(801L, -1, new RequestPagination(0, 10));
        assertThat(nonMatching).isNull();
    }

    @Test
    void requestPaginationParameterIsSkippedRegardlessOfItsParameterName() {
        User user = new User();
        user.setId(802L);
        user.setName("Renamed Pagination User");
        userRepository.create(user);

        User result = userRepository.findByIdWithMinAge(802L, 0, new RequestPagination(0, 10));

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(802L);
    }
}
