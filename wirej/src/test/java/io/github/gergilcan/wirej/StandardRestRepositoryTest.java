package io.github.gergilcan.wirej;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.entities.Product;
import io.github.gergilcan.wirej.entities.User;

/**
 * Exercises the generated implementation of an interface that inherits its
 * entire CRUD surface from StandardRestRepository<User, Long> with zero
 * methods of its own - the primary thing under test is that this compiles at
 * all (proving the annotation processor substituted real Long/User/Map
 * signatures instead of leaving dangling ID/T type variables); the HTTP
 * assertions are secondary confirmation that dispatch actually works.
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class StandardRestRepositoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCrudLifecycleThroughTheInheritedStandardMethods() throws Exception {
        User user = new User();
        user.setId(901L);
        user.setName("Generic User");

        mockMvc.perform(post("/users-generic/")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(901))
                .andExpect(jsonPath("$.name").value("Generic User"));

        mockMvc.perform(get("/users-generic/901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(901))
                .andExpect(jsonPath("$.name").value("Generic User"));

        mockMvc.perform(get("/users-generic/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(patch("/users-generic/901")
                .contentType("application/json")
                .content("{\"name\":\"Patched User\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Patched User"));

        mockMvc.perform(delete("/users-generic/901"))
                .andExpect(status().isNoContent());
    }

    @Test
    void pagedControllerGetAllReturnsDataAndTotalCountInOneResponseBody() throws Exception {
        Product product = new Product();
        product.setId(2901L);
        product.setName("Paged HTTP Product");

        mockMvc.perform(post("/products-paged/")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2901));

        mockMvc.perform(get("/products-paged/2901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paged HTTP Product"));

        mockMvc.perform(get("/products-paged/")
                .param("filters", "id==2901")
                .param("pageNumber", "0")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(2901));

        mockMvc.perform(patch("/products-paged/2901")
                .contentType("application/json")
                .content("{\"name\":\"Patched Paged Product\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Patched Paged Product"));

        mockMvc.perform(delete("/products-paged/2901"))
                .andExpect(status().isNoContent());
    }
}
