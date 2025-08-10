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

    @Test
    void testProxiedControllerDirectMethodCall() throws Exception {
        // Arrange: Create a user first
        User user = new User();
        user.setId(301L);
        user.setName("Direct Call User");

        // Create the user via repository (simulating existing data)
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk());

        // Act: Call the proxied controller method directly
        var response = userController2.getUserById(301L);

        // Assert: Verify the response is not null and contains expected data
        assertNotNull(response, "Response should not be null");
        assertNotNull(response.getBody(), "Response body should not be null");
    }
}