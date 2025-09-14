package io.github.gergilcan.wirej;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gergilcan.wirej.entities.User;

/**
 * Comprehensive test suite for UserController2 that validates role-based access
 * control.
 * Tests both allowed and forbidden scenarios for each role defined in
 * rbac-permissions.yaml.
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc(addFilters = true)
@Import(TestSecurityConfig.class)
class UserControllerRoleBasedAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1000L);
        testUser.setName("Role Test User");
    }

    // ==================== PARTICIPANT ROLE TESTS ====================
    // Participant has READ permission only

    @Test
    void participantCanReadUser() throws Exception {
        // First create a user as admin
        createUserAsAdmin();

        // Participant should be able to read
        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "Participant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1000))
                .andExpect(jsonPath("$.name").value("Role Test User"));
    }

    @Test
    void participantCanGetFilteredUsers() throws Exception {
        // First create a user as admin
        createUserAsAdmin();

        // Participant should be able to read with filters
        mockMvc.perform(get("/users/")
                .param("filters", "id==1000")
                .header("x-auth-role", "Participant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1000));
    }

    @Test
    void participantCanCountUsers() throws Exception {
        // Participant should be able to count (READ permission)
        mockMvc.perform(get("/users/count")
                .header("x-auth-role", "Participant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNumber());
    }

    @Test
    void participantCannotCreateUser() throws Exception {
        // Participant lacks WRITE permission
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "Participant")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void participantCannotDeleteUser() throws Exception {
        // First create a user as admin
        createUserAsAdmin();

        // Participant lacks DELETE permission
        mockMvc.perform(delete("/users/1000")
                .header("x-auth-role", "Participant"))
                .andExpect(status().isForbidden());
    }

    // ==================== COACH ROLE TESTS ====================
    // Coach has READ and WRITE permissions

    @Test
    void coachCanReadUser() throws Exception {
        // First create a user as admin
        createUserAsAdmin();

        // Coach should be able to read
        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "Coach"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1000))
                .andExpect(jsonPath("$.name").value("Role Test User"));
    }

    @Test
    void coachCanCreateUser() throws Exception {
        // Coach has WRITE permission
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "Coach")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated());
    }

    @Test
    void coachCannotDeleteUser() throws Exception {
        // First create a user as coach
        createUserAsCoach();

        // Coach lacks DELETE permission
        mockMvc.perform(delete("/users/1000")
                .header("x-auth-role", "Coach"))
                .andExpect(status().isForbidden());
    }

    // ==================== COACH COORDINATOR ROLE TESTS ====================
    // Coach Coordinator has READ, WRITE, and DELETE permissions

    @Test
    void coachCoordinatorCanReadUser() throws Exception {
        // First create a user as admin
        createUserAsAdmin();

        // Coach Coordinator should be able to read
        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "Coach Coordinator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1000))
                .andExpect(jsonPath("$.name").value("Role Test User"));
    }

    @Test
    void coachCoordinatorCanCreateUser() throws Exception {
        // Coach Coordinator has WRITE permission
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "Coach Coordinator")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated());
    }

    @Test
    void coachCoordinatorCanDeleteUser() throws Exception {
        // First create a user as coach coordinator
        createUserAsCoachCoordinator();

        // Coach Coordinator has DELETE permission
        mockMvc.perform(delete("/users/1000")
                .header("x-auth-role", "Coach Coordinator"))
                .andExpect(status().isNoContent());
    }

    // ==================== ADMIN ROLE TESTS ====================
    // Admin has READ, WRITE, and DELETE permissions

    @Test
    void adminCanReadUser() throws Exception {
        // First create a user as admin
        createUserAsAdmin();

        // Admin should be able to read
        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1000))
                .andExpect(jsonPath("$.name").value("Role Test User"));
    }

    @Test
    void adminCanCreateUser() throws Exception {
        // Admin has WRITE permission
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "Admin")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCanDeleteUser() throws Exception {
        // First create a user as admin
        createUserAsAdmin();

        // Admin has DELETE permission
        mockMvc.perform(delete("/users/1000")
                .header("x-auth-role", "Admin"))
                .andExpect(status().isNoContent());
    }

    // ==================== UNAUTHORIZED ACCESS TESTS ====================

    @Test
    void unauthorizedUserCannotAccessAnyEndpoint() throws Exception {
        // No authentication header provided
        mockMvc.perform(get("/users/1000"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/users/1000"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRoleCannotAccessAnyEndpoint() throws Exception {
        // Non-existent role
        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "UnknownRole"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "UnknownRole")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PUBLIC ENDPOINT TESTS ====================
    // getGenders() endpoint has no @ValidatePermission annotation

    @Test
    void anyRoleCanAccessPublicEndpoints() throws Exception {
        // Test that endpoints without @ValidatePermission are accessible by any role
        mockMvc.perform(get("/users/genders")
                .header("x-auth-role", "Participant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value("Male"));

        mockMvc.perform(get("/users/genders")
                .header("x-auth-role", "Coach"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/genders")
                .header("x-auth-role", "Admin"))
                .andExpect(status().isOk());
    }

    // ==================== HELPER METHODS ====================

    private void createUserAsAdmin() throws Exception {
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "Admin")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated());
    }

    private void createUserAsCoach() throws Exception {
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "Coach")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated());
    }

    private void createUserAsCoachCoordinator() throws Exception {
        mockMvc.perform(post("/users/create")
                .contentType("application/json")
                .header("x-auth-role", "Coach Coordinator")
                .content(objectMapper.writeValueAsString(testUser)))
                .andExpect(status().isCreated());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    void testCaseInsensitiveRoleMatching() throws Exception {
        // Test that role matching is case-insensitive (based on
        // RbacPermissionsRegistry.findByRole)
        createUserAsAdmin();

        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "admin")) // lowercase
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "ADMIN")) // uppercase
                .andExpect(status().isOk());
    }

    @Test
    void testMultiplePermissionsOnSameEndpoint() throws Exception {
        // Verify that roles with multiple permissions can access endpoints requiring
        // any of them
        createUserAsAdmin();

        // Admin has READ, WRITE, DELETE - should access READ endpoint
        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "Admin"))
                .andExpect(status().isOk());

        // Coach has READ, WRITE - should access READ endpoint
        mockMvc.perform(get("/users/1000")
                .header("x-auth-role", "Coach"))
                .andExpect(status().isOk());
    }
}
