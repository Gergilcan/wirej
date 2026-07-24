package io.github.gergilcan.wirej;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises PagedBatchController's single-object-or-array dispatch on the
 * same POST/PUT/PATCH route: the request body is sniffed at runtime, not
 * routed by a separate endpoint. Regression cases (single object) confirm the
 * existing single-item behavior still works when batch support is layered
 * on; the array cases confirm the batch path, including a PATCH batch
 * with heterogeneous per-item changed fields (proving the shape-grouping,
 * not just the uniform case). PUT is the full-replace counterpart to PATCH,
 * sharing create's array/single "shape" dispatch.
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class BatchRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void singleObjectPostStillWorksOnTheBatchCapableEndpoint() throws Exception {
        mockMvc.perform(post("/products-paged-batch/")
                .contentType("application/json")
                .content("{\"id\":4001,\"name\":\"Solo Product\",\"price\":9.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4001))
                .andExpect(jsonPath("$.name").value("Solo Product"));
    }

    @Test
    void arrayBodyPostCreatesEveryElementInOneBatch() throws Exception {
        mockMvc.perform(post("/products-paged-batch/")
                .contentType("application/json")
                .content("[{\"id\":4002,\"name\":\"Batch A\",\"price\":1.0},"
                        + "{\"id\":4003,\"name\":\"Batch B\",\"price\":2.0}]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void singleObjectPatchTakesIdFromTheBodyInsteadOfThePath() throws Exception {
        mockMvc.perform(post("/products-paged-batch/")
                .contentType("application/json")
                .content("{\"id\":4004,\"name\":\"Before Patch\",\"price\":5.0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/products-paged-batch/")
                .contentType("application/json")
                .content("{\"id\":4004,\"name\":\"After Patch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4004))
                .andExpect(jsonPath("$.name").value("After Patch"))
                .andExpect(jsonPath("$.price").value(5.0));
    }

    @Test
    void singleObjectPutFullyReplacesTheEntityIdentifiedInTheBody() throws Exception {
        mockMvc.perform(post("/products-paged-batch/")
                .contentType("application/json")
                .content("{\"id\":4008,\"name\":\"Before Put\",\"price\":5.0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/products-paged-batch/")
                .contentType("application/json")
                .content("{\"id\":4008,\"name\":\"After Put\",\"price\":7.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4008))
                .andExpect(jsonPath("$.name").value("After Put"))
                .andExpect(jsonPath("$.price").value(7.5));
    }

    @Test
    void arrayBodyPutReplacesEveryElementInOneBatch() throws Exception {
        mockMvc.perform(post("/products-paged-batch/")
                .contentType("application/json")
                .content("[{\"id\":4009,\"name\":\"Put A\",\"price\":1.0},"
                        + "{\"id\":4010,\"name\":\"Put B\",\"price\":2.0}]"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/products-paged-batch/")
                .contentType("application/json")
                .content("[{\"id\":4009,\"name\":\"Put A New\",\"price\":11.0},"
                        + "{\"id\":4010,\"name\":\"Put B New\",\"price\":22.0}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void arrayBodyPatchWithHeterogeneousChangedFieldsUpdatesEveryElement() throws Exception {
        mockMvc.perform(post("/products-paged-batch/")
                .contentType("application/json")
                .content("[{\"id\":4005,\"name\":\"Item A\",\"price\":1.0},"
                        + "{\"id\":4006,\"name\":\"Item B\",\"price\":2.0},"
                        + "{\"id\":4007,\"name\":\"Item C\",\"price\":3.0}]"))
                .andExpect(status().isCreated());

        // Three distinct changed-field shapes in one call: name-only, price-only, both.
        mockMvc.perform(patch("/products-paged-batch/")
                .contentType("application/json")
                .content("[{\"id\":4005,\"name\":\"Item A Renamed\"},"
                        + "{\"id\":4006,\"price\":22.0},"
                        + "{\"id\":4007,\"name\":\"Item C Renamed\",\"price\":33.0}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
