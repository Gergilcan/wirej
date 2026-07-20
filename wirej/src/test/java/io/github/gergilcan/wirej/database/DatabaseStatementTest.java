package io.github.gergilcan.wirej.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.github.gergilcan.wirej.TestApplication;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.repositories.UserRepository;

@SpringBootTest(classes = TestApplication.class)
class DatabaseStatementTest {

    @Autowired
    private ConnectionHandler connectionHandler;

    @Autowired
    private UserRepository userRepository;

    @Test
    void queryWithTypeCastStillBindsTheRealParameterCorrectly() throws Exception {
        User user = new User();
        user.setId(601L);
        user.setName("Cast Test User");
        userRepository.create(user);

        DatabaseStatement<User> statement = new DatabaseStatement<>(
                "/queries/User/findByIdWithCast.sql", User.class, connectionHandler);
        statement.setParameter("id", 601L);

        User result = statement.getResult();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(601L);
        assertThat(result.getName()).isEqualTo("Cast Test User");
    }

    @Test
    void repeatedParameterNameBindsBothPlaceholders() throws Exception {
        User user = new User();
        user.setId(602L);
        user.setName("Repeat Param User");
        userRepository.create(user);

        DatabaseStatement<User> statement = new DatabaseStatement<>(
                "/queries/User/findByIdRepeated.sql", User.class, connectionHandler);
        statement.setParameter("id", 602L);

        User result = statement.getResult();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(602L);
    }
}
