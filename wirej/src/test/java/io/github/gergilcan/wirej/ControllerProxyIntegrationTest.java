package io.github.gergilcan.wirej;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.controllers.UserController2;
import io.github.gergilcan.wirej.entities.User;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class ControllerProxyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserController2 userController2;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void controllerProxyIsCreatedSuccessfully() {
        // Verify that the controller proxy bean is created and injected
        assertNotNull(userController2, "Controller proxy should be auto-configured");
    }

    @Test
    void testProxiedControllerWithServiceMethod() throws Exception {
        // Arrange: Create a user using the proxied controller
        User user = new User();
        user.setId(201L);
        user.setName("Proxy User");

        // Act: Test the POST endpoint with @ServiceMethod("create")
        mockMvc.perform(post("/users2/create")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated());

        // Act & Assert: Test the GET endpoint with @ServiceMethod (uses method name)
        mockMvc.perform(get("/users2/201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(201))
                .andExpect(jsonPath("$.name").value("Proxy User"));
    }
}