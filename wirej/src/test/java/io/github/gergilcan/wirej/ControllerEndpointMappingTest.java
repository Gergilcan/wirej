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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.entities.User;

/**
 * Test to verify that controller proxies properly handle HTTP endpoints
 * with inherited annotations from the interface methods.
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc
class ControllerEndpointMappingTest {
        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void testProxyControllerHandlesHttpEndpointsWithAnnotationInheritance() throws Exception {
                // Create a test user
                User user = new User();
                user.setId(999L);
                user.setName("Endpoint Test User");

                // Test POST endpoint - should inherit @PostMapping("/create") from interface
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Test GET endpoint - should inherit @GetMapping("/{id}") from interface
                // and @PathVariable annotation should work correctly
                mockMvc.perform(get("/users2/999"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(999))
                                .andExpect(jsonPath("$.name").value("Endpoint Test User"));
        }

        @Test
        void testControllerProxyInheritsRequestMappingFromInterface() throws Exception {
                // The UserController2 interface has @RequestMapping("/users2")
                // This test verifies the proxy correctly inherits this base path

                User user = new User();
                user.setId(888L);
                user.setName("Base Path Test");

                // Create user first
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Verify the base path is correctly applied
                mockMvc.perform(get("/users2/888"))
                                .andExpect(status().isOk());
        }

        @Test
        void testParameterAnnotationsAreInherited() throws Exception {
                // Test that @PathVariable, @RequestBody annotations are properly inherited
                User user = new User();
                user.setId(777L);
                user.setName("Annotation Inheritance Test");

                // Test @RequestBody annotation inheritance
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Test @PathVariable annotation inheritance
                mockMvc.perform(get("/users2/777"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(777));
        }

        @Test
        void testParameterAnnotationsAreInheritedWithPagination() throws Exception {
                // Test that @PathVariable, @RequestBody annotations are properly inherited
                User user = new User();
                user.setId(501L);
                user.setName("Annotation Inheritance Test");
                User user2 = new User();
                user2.setId(502L);
                user2.setName("Annotation Inheritance Test");
                User user3 = new User();
                user3.setId(503L);
                user3.setName("Annotation Inheritance Test");
                User user4 = new User();
                user4.setId(504L);
                user4.setName("Annotation Inheritance Test");
                // Test @RequestBody annotation inheritance
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user2)))
                                .andExpect(status().isCreated());
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user3)))
                                .andExpect(status().isCreated());
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user4)))
                                .andExpect(status().isCreated());
                // Test that the 2 first elements are returned and not more than that
                mockMvc.perform(get("/users2/").param("pageNumber", "0").param("pageSize", "2"))
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
                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated());

                // Test @PathVariable annotation inheritance
                mockMvc.perform(get("/users2/").param("filters", "id==500"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(500));
        }

        @Test
        void testResponseStatusAnnotationIsInherited() throws Exception {
                // UserController2.createUser() has @ResponseStatus(HttpStatus.CREATED)
                // Verify this annotation is properly inherited by the proxy
                User user = new User();
                user.setId(666L);
                user.setName("Status Test User");

                mockMvc.perform(post("/users2/create")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(user)))
                                .andExpect(status().isCreated()); // Should return 201, not 200

                // getUserById has @ResponseStatus(HttpStatus.OK)
                mockMvc.perform(get("/users2/666"))
                                .andExpect(status().isOk()); // Should return 200
        }
}
