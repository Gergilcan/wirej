package io.github.gergilcan.wirej;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.repositories.UserRepository;
// For building requests like get() and post()
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// For response matchers like status(), content(), and jsonPath()
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class AutoConfigurationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // KEY CHANGE: Inject your custom repository interface.
    // Spring will find the concrete implementation bean that your library
    // generated.
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void libraryAutoConfiguresRepositoryBean() {
        // This test passes if your library successfully created the repository
        // bean and it was injected into the test context by Spring.
        assertNotNull(userRepository);
    }

    @Test
    void testCustomRepositorySaveAndFind() throws Exception {
        // Arrange
        User newUser = new User();
        newUser.setId(1L);
        newUser.setName("John Doe");

        // Act: Use the methods from your BaseRepository interface
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk());

        User foundUser = userRepository.findById(1L);

        // Assert
        assertNotNull(foundUser);
        assertEquals("John Doe", foundUser.getName());
    }

    @Test
    void testAutoConfiguredController() throws Exception {
        // This test remains the same, but it now implicitly tests that
        // the auto-configured controller is correctly using the
        // auto-configured custom repository.

        // Arrange: first, save a user to the DB
        User user = new User();
        user.setId(101L);
        user.setName("Jane Smith");

        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk());

        // Act & Assert: Test the GET endpoint
        mockMvc.perform(get("/users/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.name").value("Jane Smith"));
    }
}
