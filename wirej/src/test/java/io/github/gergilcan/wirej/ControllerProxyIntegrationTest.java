package io.github.gergilcan.wirej;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.controllers.UserController;
import io.github.gergilcan.wirej.entities.User;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@Import(TestSecurityConfig.class)
class ControllerProxyIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserController userController;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void controllerProxyIsCreatedSuccessfully() {
                // Verify that the controller proxy bean is created and injected
                assertNotNull(userController, "Controller proxy should be auto-configured");
        }

        @Test
        void testProxiedControllerWithServiceMethod() throws Exception {
                // Arrange: Create a user using the proxied controller
                User user = new User();
                user.setName("Proxy User");

                // Act: Test the POST endpoint with @ServiceMethod("create")
                mockMvc.perform(post("/users/create/201")
                                .contentType("application/json")
                                .header("x-auth-role", "ADMIN")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Act & Assert: Test the GET endpoint with @ServiceMethod (uses method name)
                mockMvc.perform(get("/users/201")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(201))
                                .andExpect(jsonPath("$.name").value("Proxy User"));
        }

        @Test
        void testProxiedControllerWithServiceMethodCreationAndDeletion() throws Exception {
                // Arrange: Create a user using the proxied controller
                User user = new User();
                user.setName("Proxy User");

                // Act: Test the POST endpoint with @ServiceMethod("create")
                mockMvc.perform(post("/users/create/201")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Act & Assert: Test the GET endpoint with @ServiceMethod (uses method name)
                mockMvc.perform(delete("/users/201")
                                .header("x-auth-role", "ADMIN")).andExpect(status().isNoContent());
        }

        @Test
        void testProxiedControllerWithServiceMethodCountByFilters() throws Exception {
                // Arrange: Create a user using the proxied controller
                User user = new User();
                user.setName("Proxy User");

                // Act: Test the POST endpoint with @ServiceMethod("create")
                mockMvc.perform(post("/users/create/209")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Act & Assert: Test the GET endpoint with @ServiceMethod("countByFilters")
                mockMvc.perform(get("/users/count")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isNumber());
        }

        @Test
        void testProxiedControllerWithServiceMethodGetGenders() throws Exception {
                // Act & Assert: Test the GET endpoint with @ServiceMethod("countByFilters")
                mockMvc.perform(get("/users/genders")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$[0]").value("Male"))
                                .andExpect(jsonPath("$[1]").value("Female"))
                                .andExpect(jsonPath("$[2]").value("Other"));
        }
}