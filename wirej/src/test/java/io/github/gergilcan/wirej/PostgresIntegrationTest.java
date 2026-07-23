package io.github.gergilcan.wirej;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.Invoice;
import io.github.gergilcan.wirej.entities.Product;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.exceptions.WireJException;
import io.github.gergilcan.wirej.repositories.InvoiceRepository;
import io.github.gergilcan.wirej.repositories.ProductRepository;
import io.github.gergilcan.wirej.repositories.UserRepository;

/**
 * Full-surface integration tests against a real PostgreSQL (WireJ's actual
 * target database - the entity mapper is Postgres-specific; the rest of the
 * suite runs on H2 as a fast stand-in). Covers generated StandardRepository
 * CRUD, hand-written @QueryFile methods, every RSQL filter operator and
 * combination, sorting, pagination, their combinations, batch operations,
 * exception propagation, and both controller stacks over HTTP.
 *
 * Skipped automatically when Docker isn't available. Each test uses its own
 * id range - the container (and schema) is shared across the whole class.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@AutoConfigureMockMvc
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Product newProduct(long id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        return product;
    }

    private User newUser(long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    private Invoice newInvoice(long invoiceNumber, String description) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setDescription(description);
        return invoice;
    }

    private static RequestFilters filters(String filters, String sort) {
        return new RequestFilters(filters, null, sort);
    }

    @Nested
    class StandardRepositoryCrud {

        @Test
        void productLifecycleWithJakartaAnnotations() {
            productRepository.create(newProduct(3001L, "PG Widget"));

            Product fetched = productRepository.get(3001L);
            assertThat(fetched).isNotNull();
            assertThat(fetched.getName()).isEqualTo("PG Widget");

            Product updated = productRepository.update(3001L, Map.of("name", "PG Widget v2"));
            assertThat(updated.getName()).isEqualTo("PG Widget v2");

            productRepository.delete(3001L);
            assertThat(productRepository.get(3001L)).isNull();
        }

        @Test
        void invoiceLifecycleWithCustomPrimaryKeyColumn() {
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber(3101L);
            invoice.setDescription("PG Invoice");
            invoiceRepository.create(invoice);

            Invoice fetched = invoiceRepository.get(3101L);
            assertThat(fetched).isNotNull();
            assertThat(fetched.getInvoiceNumber()).isEqualTo(3101L);

            Invoice updated = invoiceRepository.update(3101L, Map.of("description", "PG Invoice Amended"));
            assertThat(updated.getDescription()).isEqualTo("PG Invoice Amended");

            invoiceRepository.delete(3101L);
            assertThat(invoiceRepository.get(3101L)).isNull();
        }

        @Test
        void updateValidationRejectsUnknownKeysThePrimaryKeyAndEmptyMaps() {
            productRepository.create(newProduct(3201L, "Guarded"));

            assertThrows(WireJException.class,
                    () -> productRepository.update(3201L, Map.of("name = 'x' WHERE 1=1; --", "evil")));
            assertThrows(WireJException.class, () -> productRepository.update(3201L, Map.of("id", 9L)));
            assertThrows(WireJException.class, () -> productRepository.update(3201L, Map.of()));
            assertThrows(WireJException.class,
                    () -> invoiceRepository.update(3201L, Map.of("invoiceNumber", 9L)));
            assertThrows(WireJException.class,
                    () -> invoiceRepository.update(3201L, Map.of("invoice_number", 9L)));
        }
    }

    @Nested
    class FiltersAndOperators {

        private void seed() {
            if (productRepository.get(4001L) == null) {
                productRepository.create(newProduct(4001L, "Alpha"));
                productRepository.create(newProduct(4002L, "Beta"));
                productRepository.create(newProduct(4003L, "Gamma"));
                productRepository.create(newProduct(4004L, "Alphabet"));
            }
        }

        private Product[] query(String filterExpression) {
            seed();
            return productRepository.getAll(
                    filters("(" + filterExpression + ");id>=4001;id<=4004", "id==ASC"));
        }

        @Test
        void equalsOperator() {
            Product[] result = query("name==Beta");
            assertThat(result).hasSize(1);
            assertThat(result[0].getId()).isEqualTo(4002L);
        }

        @Test
        void notEqualsOperator() {
            Product[] result = query("name!=Beta");
            assertThat(Arrays.stream(result).map(Product::getName)).doesNotContain("Beta").hasSize(3);
        }

        @Test
        void comparisonOperators() {
            assertThat(query("id>4002")).hasSize(2);
            assertThat(query("id>=4002")).hasSize(3);
            assertThat(query("id<4002")).hasSize(1);
            assertThat(query("id<=4002")).hasSize(2);
        }

        @Test
        void likeOperatorMatchesSubstrings() {
            Product[] result = query("name=in=Alpha");
            assertThat(Arrays.stream(result).map(Product::getName)).containsExactly("Alpha", "Alphabet");
        }

        @Test
        void notLikeOperatorExcludesSubstrings() {
            Product[] result = query("name=out=Alpha");
            assertThat(Arrays.stream(result).map(Product::getName)).containsExactly("Beta", "Gamma");
        }

        @Test
        void andCombination() {
            Product[] result = query("id>4001;name=in=a");
            assertThat(result).isNotEmpty();
            assertThat(Arrays.stream(result).map(Product::getId)).allMatch(id -> id > 4001L);
        }

        @Test
        void orCombination() {
            Product[] result = query("name==Alpha,name==Gamma");
            assertThat(Arrays.stream(result).map(Product::getName)).containsExactly("Alpha", "Gamma");
        }

        @Test
        void nestedGroupsAreRespected() {
            Product[] result = query("(id==4001;name==Alpha),id==4003");
            assertThat(Arrays.stream(result).map(Product::getId)).containsExactly(4001L, 4003L);
        }

        @Test
        void unparenthesizedMixFollowsStandardRsqlPrecedenceAndBindsTighter() {
            // OR(name==Beta, AND(name==Gamma, id>=4003))
            Product[] result = query("name==Beta,name==Gamma;id>=4003");
            assertThat(Arrays.stream(result).map(Product::getName)).containsExactly("Beta", "Gamma");
        }

        @Test
        void malformedFilterThrowsInsteadOfSilentlyDropping() {
            seed();
            WireJException ex = assertThrows(WireJException.class,
                    () -> productRepository.getAll(filters("name~~~bogus~~~", "id==ASC")));
            assertThat(ex.getMessage()).contains("Unrecognized filter clause");
        }
    }

    @Nested
    class SortingAndPagination {

        private void seed() {
            if (productRepository.get(5001L) == null) {
                productRepository.create(newProduct(5001L, "Cherry"));
                productRepository.create(newProduct(5002L, "Apple"));
                productRepository.create(newProduct(5003L, "Banana"));
                productRepository.create(newProduct(5004L, "Apple"));
            }
        }

        private Product[] sorted(String sort) {
            seed();
            return productRepository.getAll(filters("id>=5001;id<=5004", sort));
        }

        @Test
        void ascendingAndDescendingSort() {
            Product[] asc = sorted("id==ASC");
            assertThat(Arrays.stream(asc).map(Product::getId)).containsExactly(5001L, 5002L, 5003L, 5004L);

            Product[] desc = sorted("id==DESC");
            assertThat(Arrays.stream(desc).map(Product::getId)).containsExactly(5004L, 5003L, 5002L, 5001L);
        }

        @Test
        void multipleSortClausesApplyInOrder() {
            Product[] result = sorted("name==ASC;id==DESC");
            assertThat(Arrays.stream(result).map(Product::getId)).containsExactly(5004L, 5002L, 5003L, 5001L);
        }

        @Test
        void countMatchesFiltersIndependentlyOfPageSize() {
            seed();
            Long total = productRepository.count(filters("id>=5001;id<=5004", "id==ASC"));
            assertThat(total).isEqualTo(4L);

            Long filtered = productRepository.count(filters("id>=5001;id<=5004;name==Apple", "id==ASC"));
            assertThat(filtered).isEqualTo(2L);
        }
    }

    @Nested
    class HandWrittenQueryFileMethods {

        @Test
        void findByIdAndCreate() {
            userRepository.create(newUser(6001L, "PG User"));

            User fetched = userRepository.findById(6001L);
            assertThat(fetched).isNotNull();
            assertThat(fetched.getName()).isEqualTo("PG User");
        }

        @Test
        void findByFiltersWithPaginationAndSorting() {
            userRepository.create(newUser(6101L, "Filtered A"));
            userRepository.create(newUser(6102L, "Filtered B"));
            userRepository.create(newUser(6103L, "Filtered C"));

            User[] page = userRepository.findByFilters(
                    filters("id>=6101;id<=6103", "id==DESC"), new RequestPagination(0, 2));
            assertThat(Arrays.stream(page).map(User::getId)).containsExactly(6103L, 6102L);
        }

        @Test
        void explicitCountOperationsAndClassParameterSkipping() {
            userRepository.create(newUser(6201L, "Counted"));

            Long tally = userRepository.tallyUsers(filters("id==6201", "id==DESC"));
            assertThat(tally).isEqualTo(1L);

            Long viaClassParam = userRepository.tallyUsersWithEntityClass(filters("id==6201", "id==DESC"),
                    User.class);
            assertThat(viaClassParam).isEqualTo(1L);

            Long viaCountByFilters = userRepository.countByFilters(filters("id==6201", "id==DESC"), User.class);
            assertThat(viaCountByFilters).isEqualTo(1L);
        }

        @Test
        void explicitSelectOperationOverridesMisleadingMethodName() {
            userRepository.create(newUser(6301L, "Summary User"));

            User result = userRepository.retrieveAccountsSummary(6301L);
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Summary User");
        }

        @Test
        void extraScalarParameterBindsBySnakeCaseName() {
            userRepository.create(newUser(6401L, "Min Age User"));

            User matching = userRepository.findByIdWithMinAge(6401L, 5, new RequestPagination(0, 10));
            assertThat(matching).isNotNull();

            User nonMatching = userRepository.findByIdWithMinAge(6401L, -1, new RequestPagination(0, 10));
            assertThat(nonMatching).isNull();
        }

        @Test
        void batchDeleteRemovesAllGivenIds() {
            userRepository.create(newUser(6501L, "Batch 1"));
            userRepository.create(newUser(6502L, "Batch 2"));
            userRepository.create(newUser(6503L, "Batch 3"));

            userRepository.delete(new Long[] { 6501L, 6502L, 6503L });

            assertThat(userRepository.findById(6501L)).isNull();
            assertThat(userRepository.findById(6502L)).isNull();
            assertThat(userRepository.findById(6503L)).isNull();
        }

        @Test
        void mixedInterfaceServesItsHandWrittenCountNextToGeneratedCrud() {
            productRepository.create(newProduct(6601L, "Mixed"));

            assertThat(productRepository.countProducts()).isGreaterThanOrEqualTo(1L);
        }

        @Test
        void duplicateKeySurfacesAsWireJExceptionWithSqlCause() {
            userRepository.create(newUser(6701L, "Dup"));

            WireJException ex = assertThrows(WireJException.class,
                    () -> userRepository.create(newUser(6701L, "Dup")));
            assertThat(ex.getCause()).isInstanceOf(SQLException.class);
        }
    }

    @Nested
    class HttpControllers {

        @Test
        void standardRestControllerFullCrudOverHttp() throws Exception {
            User user = newUser(7001L, "HTTP Generic");

            mockMvc.perform(post("/users-generic/")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(user)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(7001));

            mockMvc.perform(get("/users-generic/7001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("HTTP Generic"));

            mockMvc.perform(patch("/users-generic/7001")
                    .contentType("application/json")
                    .content("{\"name\":\"HTTP Patched\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("HTTP Patched"));

            mockMvc.perform(delete("/users-generic/7001"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void listEndpointHonorsFilterAndPaginationQueryParameters() throws Exception {
            for (long id = 7101L; id <= 7104L; id++) {
                mockMvc.perform(post("/users-generic/")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(newUser(id, "Page User " + id))))
                        .andExpect(status().isCreated());
            }

            mockMvc.perform(get("/users-generic/")
                    .param("filters", "id>=7101;id<=7104")
                    .param("sort", "id==DESC")
                    .param("pageNumber", "0")
                    .param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(7104))
                    .andExpect(jsonPath("$[1].id").value(7103));

            mockMvc.perform(get("/users-generic/")
                    .param("filters", "id>=7101;id<=7104")
                    .param("sort", "id==DESC")
                    .param("pageNumber", "1")
                    .param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(7102))
                    .andExpect(jsonPath("$[1].id").value(7101));
        }

        @Test
        void classicServiceMethodControllerEndpoints() throws Exception {
            mockMvc.perform(post("/users2/create")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(newUser(7201L, "Classic User"))))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/users2/7201"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Classic User"));

            mockMvc.perform(get("/users2/")
                    .param("filters", "id==7201")
                    .param("pageNumber", "0")
                    .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(7201));

            mockMvc.perform(get("/users2/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isNumber());

            mockMvc.perform(get("/users2/genders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("Male"));

            mockMvc.perform(delete("/users2/7201"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void batchControllerSniffsSingleVsArrayBodyOnTheSamePostAndPatchRoute() throws Exception {
            mockMvc.perform(post("/products-paged-batch/")
                    .contentType("application/json")
                    .content("{\"id\":7301,\"name\":\"Solo Product\",\"price\":9.99}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(7301));

            mockMvc.perform(post("/products-paged-batch/")
                    .contentType("application/json")
                    .content("[{\"id\":7302,\"name\":\"Batch A\",\"price\":1.0},"
                            + "{\"id\":7303,\"name\":\"Batch B\",\"price\":2.0},"
                            + "{\"id\":7304,\"name\":\"Batch C\",\"price\":3.0}]"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)));

            mockMvc.perform(patch("/products-paged-batch/")
                    .contentType("application/json")
                    .content("{\"id\":7301,\"name\":\"Solo Product Patched\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7301))
                    .andExpect(jsonPath("$.name").value("Solo Product Patched"))
                    .andExpect(jsonPath("$.price").value(9.99));

            // Heterogeneous per-item changed fields in one batch call: name-only,
            // price-only, both - proves the shape-grouping, not just the uniform case.
            mockMvc.perform(patch("/products-paged-batch/")
                    .contentType("application/json")
                    .content("[{\"id\":7302,\"name\":\"Batch A Renamed\"},"
                            + "{\"id\":7303,\"price\":22.0},"
                            + "{\"id\":7304,\"name\":\"Batch C Renamed\",\"price\":33.0}]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)));
        }
    }
}
