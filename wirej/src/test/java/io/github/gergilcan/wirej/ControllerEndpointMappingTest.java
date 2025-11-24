package io.github.gergilcan.wirej;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.entities.User;

/**
 * Test to verify that controller proxies properly handle HTTP endpoints
 * with inherited annotations from the interface methods.
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc(addFilters = true)
@Import(TestSecurityConfig.class)
class ControllerEndpointMappingTest {
        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void testProxyControllerHandlesHttpEndpointsWithAnnotationInheritance() throws Exception {
                // Create a test user
                User user = new User();
                user.setName("Endpoint Test User");

                // Test POST endpoint - should inherit @PostMapping("/create") from interface
                mockMvc.perform(post("/users/create/999")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Test GET endpoint - should inherit @GetMapping("/{id}") from interface
                // and @PathVariable annotation should work correctly
                mockMvc.perform(get("/users/999")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(999))
                                .andExpect(jsonPath("$.name").value("Endpoint Test User"));
        }

        @Test
        void testControllerProxyInheritsRequestMappingFromInterface() throws Exception {
                // The UserController2 interface has @RequestMapping("/users")
                // This test verifies the proxy correctly inherits this base path

                User user = new User();
                user.setName("Base Path Test");

                // Create user first
                mockMvc.perform(post("/users/create/888")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Verify the base path is correctly applied
                mockMvc.perform(get("/users/888")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk());
        }

        @Test
        void testParameterAnnotationsAreInherited() throws Exception {
                // Test that @PathVariable, @RequestBody annotations are properly inherited
                User user = new User();
                user.setName("Annotation Inheritance Test");

                // Test @RequestBody annotation inheritance
                mockMvc.perform(post("/users/create/777")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Test @PathVariable annotation inheritance
                mockMvc.perform(get("/users/777")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(777));
        }

        @Test
        void testParameterAnnotationsAreInheritedWithPagination() throws Exception {
                // Test that @PathVariable, @RequestBody annotations are properly inherited
                User user = new User();
                user.setName("Annotation Inheritance Test");
                User user2 = new User();
                user2.setName("Annotation Inheritance Test");
                User user3 = new User();
                user3.setName("Annotation Inheritance Test");
                User user4 = new User();
                user4.setName("Annotation Inheritance Test");
                // Test @RequestBody annotation inheritance
                mockMvc.perform(post("/users/create/501")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());
                mockMvc.perform(post("/users/create/502")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user2)))
                                .andExpect(status().isCreated());
                mockMvc.perform(post("/users/create/503")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user3)))
                                .andExpect(status().isCreated());
                mockMvc.perform(post("/users/create/504")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user4)))
                                .andExpect(status().isCreated());
                // Test that the 2 first elements are returned and not more than that
                mockMvc.perform(get("/users/").param("pageNumber", "0").param("pageSize", "2")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(504))
                                .andExpect(jsonPath("$[1].id").value(503))
                                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                                .andExpect(jsonPath("$[2]").doesNotExist());

        }

        @Test
        void testParameterAnnotationsAreInheritedWithFiltering() throws Exception {
                // Test that @PathVariable, @RequestBody annotations are properly inherited
                User user = new User();
                user.setId(500L);
                user.setName("Annotation Inheritance Test");

                // Test @RequestBody annotation inheritance
                mockMvc.perform(post("/users/create/500")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Test @PathVariable annotation inheritance
                mockMvc.perform(get("/users/")
                                .header("x-auth-role", "ADMIN").param("filters", "id==500"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(500));
        }

        @Test
        void testResponseStatusAnnotationIsInherited() throws Exception {
                // UserController2.createUser() has @ResponseStatus(HttpStatus.CREATED)
                // Verify this annotation is properly inherited by the proxy
                User user = new User();
                user.setId(666L);
                user.setName("Status Test User");

                mockMvc.perform(post("/users/create/666")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated()); // Should return 201, not 200

                // getUserById has @ResponseStatus(HttpStatus.OK)
                mockMvc.perform(get("/users/666")
                                .header("x-auth-role", "ADMIN"))
                                .andExpect(status().isOk()); // Should return 200
        }

        @Test
        void testCountValueForFilteredRequest() throws Exception {
                // UserController2.createUser() has @ResponseStatus(HttpStatus.CREATED)
                // Verify this annotation is properly inherited by the proxy
                User user = new User();
                user.setName("Count Test User");

                mockMvc.perform(post("/users/create/665")
                                .header("x-auth-role", "ADMIN")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated()); // Should return 201, not 200

                // countByFilters has @ResponseStatus(HttpStatus.OK)
                mockMvc.perform(get("/users/count").param("filters", "name==Count Test User").header("x-auth-role",
                                "ADMIN"))
                                .andExpect(status().isOk()) // Should return 200
                                .andExpect(jsonPath("$").value(1));
        }
}
