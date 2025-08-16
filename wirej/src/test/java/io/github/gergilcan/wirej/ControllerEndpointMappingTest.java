package io.github.gergilcan.wirej;

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

import io.github.gergilcan.wirej.entities.User;

/**
 * Test to verify that controller proxies properly handle HTTP endpoints
 * with inherited annotations from the interface methods.
 */
@SpringBootTest(classes = TestApplication.class)
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
