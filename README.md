# WireJ

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.gergilcan/wirej)](https://central.sonatype.com/artifact/io.github.gergilcan/wirej)
[![Java](https://img.shields.io/badge/Java-21+-orange)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green)](```bash

**WireJ** is a lightweight Java framework that simplifies Spring Boot development by allowing you to write **interface-based controllers** and **SQL-file-based repositories** with compile-time validation. It eliminates boilerplate code while providing type safety and IDE support.

## 🚀 What is WireJ?

WireJ transforms how you write Spring Boot applications by:

- **🎯 Interface Controllers**: Write REST controllers as interfaces that automatically proxy to your services
- **📄 SQL File Repositories**: Define repositories that use external SQL files for queries
- **✅ Compile-time Validation**: Annotation processors ensure your method signatures and SQL files exist at build time
- **🔧 Zero Configuration**: Works out-of-the-box with Spring Boot auto-configuration
- **🎨 Clean Architecture**: Promotes separation of concerns and testable code

## 📦 Installation

### Maven

Add WireJ to your Spring Boot project:

```xml
<dependency>
    <groupId>io.github.gergilcan</groupId>
    <artifactId>wirej</artifactId>
    <version>1.0.0.12</version>
</dependency>

<!-- For compile-time validation (recommended) -->
<dependency>
    <groupId>io.github.gergilcan</groupId>
    <artifactId>wirej-processor</artifactId>
    <version>1.0.0.12</version>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```

### Gradle

```gradle
implementation 'io.github.gergilcan:wirej:1.0.0.12'
annotationProcessor 'io.github.gergilcan:wirej-processor:1.0.0.12'
```

### Maven Compiler Configuration (Required for Annotation Processing)

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.gergilcan</groupId>
                        <artifactId>wirej-processor</artifactId>
                        <version>1.0.0.12</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## 🎯 Quick Start

### 1. Create an Entity

```java
@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
}
```

### 2. Create a Repository Interface

```java
@Repository
public interface UserRepository {
    @QueryFile("/queries/users/findById.sql")
    User findById(Long id);

    @QueryFile("/queries/users/findByEmail.sql")
    Optional<User> findByEmail(String email);

    @QueryFile("/queries/users/create.sql")
    void create(User user);

    /**
     * Deletes users by their IDs.
     *
     * @param ids the IDs of the users to delete, JsonAlias can be used to specify the name of the parameter used inside
     * the query
     */
    @QueryFile(value = "/queries/users/delete.sql", isBatch = true)
    void delete(@JsonAlias("id") Long[] ids);
}
```

### 3. Create SQL Files

**src/main/resources/queries/users/findById.sql**

```sql
SELECT id, name, email
FROM users
WHERE id = :id
```

**src/main/resources/queries/users/findByEmail.sql**

```sql
SELECT id, name, email
FROM users
WHERE email = :email
```

**src/main/resources/queries/users/create.sql**

```sql
INSERT INTO users (id, name, email)
VALUES (:id, :name, :email)
```

**src/main/resources/queries/users/delete.sql**

```sql
DELETE FROM users
WHERE id = :id
```

### 4. Create a Service

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User getUserById(Long id) {
        User user = userRepository.findById(id);
        if (user == null) {
            throw new EntityNotFoundException("User not found");
        }
        return user;
    }

    public User createUser(User user) {
        // Check if email already exists
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        userRepository.create(user);
        return user;
    }
}
```

### 5. Create a Controller Interface

```java
@RestController
@RequestMapping("/api/users")
@ServiceClass(UserService.class)
public interface UserController {

    @GetMapping("/{id}")
    @ServiceMethod  // Maps to getUserById(Long id)
    ResponseEntity<User> getUserById(@PathVariable Long id);

    @PostMapping
    @ServiceMethod("createUser")  // Explicitly maps to createUser(User user)
    ResponseEntity<User> create(@RequestBody User user);
}
```

### 6. Configure Package Scanning (Optional)

**application.properties**

```properties
wirej.scan.packages=com.yourcompany.controllers
wirej.debug=true
```

That's it! WireJ will automatically:

- ✅ Create proxy implementations for your controller interfaces
- ✅ Wire them to your services using method name matching
- ✅ Validate at compile-time that methods exist and SQL files are present
- ✅ Generate proper Spring beans and register them

## 🔄 Migration Guide

### From Traditional Spring Boot Controllers

**Before (Traditional Controller):**

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User created = userService.createUser(user);
        return ResponseEntity.ok(created);
    }
}
```

**After (WireJ Interface Controller):**

```java
@RestController
@RequestMapping("/api/users")
@ServiceClass(UserService.class)
public interface UserController {

    @GetMapping("/{id}")
    @ServiceMethod  // Auto-maps to getUserById
    ResponseEntity<User> getUserById(@PathVariable Long id);

    @PostMapping
    @ServiceMethod("createUser")  // Explicit mapping
    ResponseEntity<User> createUser(@RequestBody User user);
}
```

### From JPA Repositories

**Before (JPA Repository):**

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    @Modifying
    @Query("UPDATE User u SET u.name = :name WHERE u.id = :id")
    void updateName(@Param("id") Long id, @Param("name") String name);
}
```

**After (WireJ SQL File Repository):**

```java
@Repository
public interface UserRepository {
    @QueryFile("/queries/users/findById.sql")
    User findById(Long id);

    @QueryFile("/queries/users/findByEmail.sql")
    Optional<User> findByEmail(String email);

    @QueryFile("/queries/users/updateName.sql")
    void updateName(Long id, String name);
}
```

**queries/users/updateName.sql:**

```sql
UPDATE users
SET name = :name
WHERE id = :id
```

## 🎨 Key Features

### Interface-Based Controllers

- **Clean Separation**: Controllers are interfaces, services contain business logic
- **Automatic Wiring**: Methods are automatically mapped to service methods
- **Type Safety**: Compile-time validation ensures method signatures match
- **Flexible Mapping**: Use `@ServiceMethod` for explicit method name mapping

### SQL File Repositories

- **External SQL**: Keep SQL in separate files for better maintainability
- **Version Control Friendly**: Easy to diff and review SQL changes
- **IDE Support**: Syntax highlighting and validation in SQL files
- **Named Parameters**: Use `:paramName` syntax for parameters

### Compile-Time Validation

The annotation processor validates:

- ✅ Service methods exist and have matching signatures
- ✅ SQL files exist in the specified paths
- ✅ Parameter names match between methods and SQL
- ❌ Compilation fails if validation errors are found

## 🎛️ Additional Features

### Response Status Control

WireJ supports setting HTTP response status codes using Spring's `@ResponseStatus` annotation. This allows you to specify the exact HTTP status code that should be returned by your endpoint.

```java
@RestController
@RequestMapping("/api/users")
@ServiceClass(UserService.class)
public interface UserController {

    @GetMapping("/hello")
    @ServiceMethod
    @ResponseStatus(HttpStatus.OK)  // Explicitly return 200 OK
    ResponseEntity<?> hello();

    @GetMapping("/{id}")
    @ServiceMethod
    @ResponseStatus(HttpStatus.OK)  // Explicitly return 200 OK
    ResponseEntity<?> findById(@PathVariable("id") Long id);

    @PostMapping
    @ServiceMethod("createUser")
    @ResponseStatus(HttpStatus.CREATED)  // Return 201 Created for new resources
    ResponseEntity<?> create(@RequestBody User user);

    @DeleteMapping("/{id}")
    @ServiceMethod("deleteUser")
    @ResponseStatus(HttpStatus.NO_CONTENT)  // Return 204 No Content for deletions
    void delete(@PathVariable("id") Long id);
}
```

### Path Variable Naming

WireJ supports explicit naming of path variables using Spring's `@PathVariable` annotation. This is especially useful when the method parameter name differs from the URL path variable name, or when you want to be explicit about the mapping.

```java
@RestController
@RequestMapping("/api")
@ServiceClass(UserService.class)
public interface UserController {

    // Explicit path variable naming
    @GetMapping("/users/{id}")
    @ServiceMethod
    ResponseEntity<?> getUserById(@PathVariable("id") Long userId);

    // Multiple path variables with explicit naming
    @GetMapping("/users/{userId}/posts/{postId}")
    @ServiceMethod("getUserPost")
    ResponseEntity<?> getPost(
        @PathVariable("userId") Long userId,
        @PathVariable("postId") Long postId
    );

    // Mixed explicit and implicit naming
    @GetMapping("/admin/{id}")
    @ServiceMethod
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findById(@PathVariable("id") Long id);

    // Query parameters with path variables
    @GetMapping("/users/{userId}/search")
    @ServiceMethod("searchUserData")
    ResponseEntity<?> searchData(
        @PathVariable("userId") Long userId,
        @RequestParam String query,
        @RequestParam(defaultValue = "10") int limit
    );

    // Using request parameter for ID instead of path variable
    @GetMapping("/search")
    @ServiceMethod("findByIdParam")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findByRequestParam(@RequestParam("id") Long id);

    // Multiple request parameters
    @GetMapping("/filter")
    @ServiceMethod("filterUsers")
    ResponseEntity<?> filterUsers(
        @RequestParam("name") String name,
        @RequestParam("email") String email,
        @RequestParam(value = "active", defaultValue = "true") boolean active
    );

    // Pagination support with request parameters
    @GetMapping("/admin/all")
    @ServiceMethod("findAllPaginated")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findAllPaginated(
        @RequestParam("pageNumber") int pageNumber,
        @RequestParam("pageSize") int pageSize
    );
}
```

### Database Pagination

WireJ supports database pagination through request parameters combined with SQL OFFSET/FETCH clauses. This allows for efficient handling of large datasets by retrieving only the required page of results.

#### Controller Definition

```java
@RestController
@RequestMapping("/api/users")
@ServiceClass(UserService.class)
public interface UserController {

    @GetMapping("/admin/all")
    @ServiceMethod("findAllPaginated")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findAllPaginated(
        @RequestParam("pageNumber") int pageNumber,
        @RequestParam("pageSize") int pageSize
    );
}
```

#### Repository with Pagination

```java
@Repository
public interface UserRepository {
    @QueryFile("/queries/users/findAllPaginated.sql")
    List<User> findAllPaginated(int pageNumber, int pageSize, int initialPosition);
}
```

#### SQL File with Pagination

**src/main/resources/queries/users/findAllPaginated.sql**

```sql
SELECT id, name, email, created_at
FROM users
ORDER BY created_at DESC
OFFSET :initialPosition ROWS FETCH NEXT :pageSize ROWS ONLY
```

#### Usage Examples

```bash
# Get first page (page 0) with 10 users
GET /api/users/admin/all?pageNumber=0&pageSize=10

# Get second page (page 1) with 20 users
GET /api/users/admin/all?pageNumber=1&pageSize=20

# Get third page (page 2) with 5 users
GET /api/users/admin/all?pageNumber=2&pageSize=5
```

### Query Filtering with RequestFilters

WireJ provides a `RequestFilters` class that allows for dynamic query filtering, searching, and sorting. This enables flexible and powerful query operations without hardcoding filter logic in your controllers.

#### RequestFilters Class

The `RequestFilters` class supports:

- **Dynamic filters**: Add multiple filter conditions using RSQL syntax
- **Search functionality**: General search across fields
- **Sorting**: Specify sort order and direction

```java
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RequestFilters {
    private String filters;    // Dynamic filter conditions (must be valid RSQL)
    private String search;     // Search term
    private String sort;       // Sort specification (default: "id==DESC")

    public void addFilter(String filter) {
        if (filters == null) {
            filters = filter;
        } else {
            filters += ";" + filter;
        }
    }
}
```

#### RSQL Filter Syntax

The `filters` parameter must use valid **RSQL (RESTful Service Query Language)** syntax:

**Basic Operators:**

- `==` : Equal to
- `!=` : Not equal to
- `=gt=` or `>` : Greater than
- `=ge=` or `>=` : Greater than or equal
- `=lt=` or `<` : Less than
- `=le=` or `<=` : Less than or equal
- `=in=` : String contains

**Logical Operators:**

- `;` or `and` : AND condition
- `,` or `or` : OR condition
- `()` : Grouping

**RSQL Examples:**

```bash
# Single condition
filters=status==ACTIVE

# Multiple conditions with AND
filters=status==ACTIVE;age>25

# Multiple conditions with OR
filters=status==ACTIVE,status==PENDING

# Complex conditions with grouping
filters=(status==ACTIVE;age>25),role==ADMIN

# Combining different operators
filters=age>=18;age<=65;status!=INACTIVE
```

#### Controller with RequestFilters

```java
@RestController
@RequestMapping("/api/users")
@ServiceClass(UserService.class)
public interface UserController {

    // Simple filtering endpoint
    @GetMapping("/filter")
    @ServiceMethod("findWithFilters")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findWithFilters(RequestFilters filters);

    // Combined pagination and filtering
    @GetMapping("/filter/paginated")
    @ServiceMethod("findWithFiltersAndPagination")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findWithFiltersAndPagination(
        RequestFilters filters,
        @RequestParam("pageNumber") int pageNumber,
        @RequestParam("pageSize") int pageSize
    );
}
```

#### Repository with RequestFilters

```java
@Repository
public interface UserRepository {
    @QueryFile("/queries/users/findWithFilters.sql")
    List<User> findWithFilters(RequestFilters filters);

    @QueryFile("/queries/users/findWithFiltersAndPagination.sql")
    List<User> findWithFiltersAndPagination(
        RequestFilters filters,
        int pageNumber,
        int pageSize,
        int initialPosition
    );
}
```

#### SQL Files with RequestFilters

**src/main/resources/queries/users/findWithFilters.sql**

```sql
SELECT id, name, email, status, created_at
FROM users
WHERE deleted = false
  AND (
    :search IS NULL OR
    LOWER(name) LIKE LOWER(CONCAT('%', :search, '%')) OR
    LOWER(email) LIKE LOWER(CONCAT('%', :search, '%'))
  )
  :filters :sorting
```

**src/main/resources/queries/users/findWithFiltersAndPagination.sql**

```sql
SELECT id, name, email, status, created_at
FROM users
WHERE deleted = false
  AND (
    :search IS NULL OR
    LOWER(name) LIKE LOWER(CONCAT('%', :search, '%')) OR
    LOWER(email) LIKE LOWER(CONCAT('%', :search, '%'))
  )
  :filters :sorting
OFFSET :initialPosition ROWS FETCH NEXT :pageSize ROWS ONLY
```

#### Service Implementation

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public List<User> findWithFilters(RequestFilters filters) {
        return userRepository.findWithFilters(filters);
    }

    public List<User> findWithFiltersAndPagination(RequestFilters filters, int pageNumber, int pageSize) {
        return userRepository.findWithFiltersAndPagination(filters, pageNumber, pageSize);
    }
}
```

#### Usage Examples

**Simple Search Request:**

```bash
GET /api/users/filter?search=john&sort=name==ASC
```

**Complex Filtering Request:**

````bash
**Complex Filtering Request:**
```bash
GET /api/users/filter?filters=status==ACTIVE;age=gt=25&search=developer&sort=created_at==DESC
````

**Multiple Filters with OR condition:**

```bash
GET /api/users/filter?filters=status==ACTIVE,status==PENDING&search=developer
```

**Complex RSQL with grouping:**

```bash
GET /api/users/filter?filters=(status==ACTIVE;age=ge=18),role==ADMIN&sort=name==ASC
```

**Paginated Filtering Request:**

```bash
GET /api/users/filter/paginated?pageNumber=0&pageSize=10&search=admin&sort=email==ASC
```

### Complete Controller Example

Here's a comprehensive example showing various features working together:

```java
@RestController
@RequestMapping("/api/users")
@ServiceClass(UserService.class)
public interface UserController {

    // Simple endpoint with custom status
    @GetMapping("/hello")
    @ServiceMethod
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> hello();

    // Overloaded methods with different parameters
    @GetMapping("/hello/{id}")
    @ServiceMethod("helloWithId")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> hello(@PathVariable("id") Long id);

    // List all with explicit status
    @GetMapping("/")
    @ServiceMethod("findAll")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findAll();

    // Get by ID with explicit path variable naming
    @GetMapping("/{id}")
    @ServiceMethod
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findById(@PathVariable("id") Long id);

    // Create with 201 status
    @PostMapping
    @ServiceMethod("createUser")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> create(@RequestBody User user);

    // Update with explicit mapping
    @PutMapping("/{userId}")
    @ServiceMethod("updateUser")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> update(
        @PathVariable("userId") Long id,
        @RequestBody User user
    );

    // Delete with no content response
    @DeleteMapping("/{id}")
    @ServiceMethod("deleteUser")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable("id") Long id);
}
```

### Service Implementation

The corresponding service would implement the mapped methods:

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public String hello() {
        return "Hello World!";
    }

    public String helloWithId(Long id) {
        return "Hello User " + id;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id);
    }

    public User createUser(User user) {
        return userRepository.create(user);
    }

    public User updateUser(Long id, User user) {
        user.setId(id);
        return userRepository.update(user);
    }

    public void deleteUser(Long id) {
        userRepository.delete(id);
    }

    public User findByIdParam(Long id) {
        // Same logic as findById, but called via request parameter
        return userRepository.findById(id);
    }

    public List<User> filterUsers(String name, String email, boolean active) {
        return userRepository.filterUsers(name, email, active);
    }

    public List<User> findAllPaginated(int pageNumber, int pageSize) {
        // Calculate the initial position (offset) for pagination
        int initialPosition = pageNumber * pageSize;
        return userRepository.findAllPaginated(pageNumber, pageSize, initialPosition);
    }
}
```

## 📋 Annotation Reference

### WireJ Annotations

### `@ServiceClass(value = ServiceClass.class)`

- **Target**: Interface (Controller)
- **Purpose**: Specifies which service class to wire the controller to
- **Required**: Yes for controller interfaces

### `@ServiceMethod(value = "methodName")`

- **Target**: Method
- **Purpose**: Maps controller method to service method
- **Optional**: If not specified, uses the controller method name
- **Example**: `@ServiceMethod("createNewUser")` maps to service's `createNewUser()` method

### `@QueryFile(value = "/path/to/query.sql")`

- **Target**: Method (Repository)
- **Purpose**: Specifies the SQL file path for the repository method
- **Required**: Yes for repository methods
- **Path**: Relative to `src/main/resources` or `src/test/resources`

### Spring Annotations Supported

WireJ works seamlessly with standard Spring annotations:

### `@ResponseStatus(HttpStatus)`

- **Target**: Method or Class
- **Purpose**: Sets the HTTP response status code
- **Examples**:
  - `@ResponseStatus(HttpStatus.CREATED)` for 201 Created
  - `@ResponseStatus(HttpStatus.NO_CONTENT)` for 204 No Content
  - `@ResponseStatus(HttpStatus.OK)` for 200 OK (default)

### `@PathVariable("variableName")`

- **Target**: Method parameter
- **Purpose**: Binds URI template variables to method parameters
- **Features**:
  - **Explicit naming**: `@PathVariable("id")` maps URL `{id}` to any parameter name
  - **Implicit naming**: `@PathVariable` uses parameter name (requires `-parameters` compiler flag)
  - **Type conversion**: Automatic conversion to parameter type (Long, String, etc.)

### `@RequestParam("paramName")`

- **Target**: Method parameter
- **Purpose**: Binds request parameters to method parameters
- **Features**:
  - **Optional parameters**: `@RequestParam(required = false)`
  - **Default values**: `@RequestParam(defaultValue = "10")`
  - **Type conversion**: Automatic conversion to parameter type

### `@RequestBody`

- **Target**: Method parameter
- **Purpose**: Binds HTTP request body to method parameter
- **Features**:
  - **JSON deserialization**: Automatic conversion from JSON to object
  - **Validation support**: Works with `@Valid` for Bean Validation

## ⚙️ Configuration

### Package Scanning

Configure which packages WireJ should scan for controllers:

```properties
# Specific packages (recommended)
wirej.scan.packages=com.yourcompany.controllers,com.yourcompany.api

# Enable debug logging
wirej.debug=true

# Disable auto-detection (default: true)
wirej.auto-detect-packages=false
```

### Auto-Detection

If no packages are specified, WireJ automatically detects your main application package by locating your `@SpringBootApplication` class.

## 🧪 Testing

WireJ components are regular Spring beans and can be tested normally:

```java
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldGetUserById() {
        ResponseEntity<User> response = restTemplate.getForEntity(
            "/api/users/1", User.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }
}
```

## 📊 Benefits

### Code Reduction

- **~50% less boilerplate** in controllers
- **Clean interfaces** instead of implementation classes
- **Separation of concerns** between web layer and business logic

### Maintainability

- **SQL in separate files** for better version control
- **Compile-time validation** catches errors early
- **IDE support** for SQL syntax highlighting

### Testability

- **Interface-based design** makes mocking easier
- **Service layer isolation** for unit testing
- **Standard Spring testing** patterns work unchanged

## 🛠️ Requirements

- **Java 21+**
- **Spring Boot 3.5+**
- **Maven 3.6+** or **Gradle 7.0+**

## 📖 Advanced Usage

### Complex Method Mapping

```java
@RestController
@RequestMapping("/api/users")
@ServiceClass(UserService.class)
public interface UserController {

    // Automatic mapping to getUserById(Long id)
    @GetMapping("/{id}")
    @ServiceMethod
    ResponseEntity<User> getUserById(@PathVariable Long id);

    // Explicit mapping to findUsersByStatus(String status)
    @GetMapping("/status/{status}")
    @ServiceMethod("findUsersByStatus")
    ResponseEntity<List<User>> getByStatus(@PathVariable String status);

    // Complex parameter mapping
    @PostMapping("/search")
    @ServiceMethod("searchUsers")
    ResponseEntity<List<User>> search(@RequestBody UserSearchCriteria criteria);
}
```

### Complex SQL Queries

**queries/users/findWithPagination.sql:**

```sql
SELECT u.id, u.name, u.email, u.created_at
FROM users u
WHERE (:name IS NULL OR u.name ILIKE CONCAT('%', :name, '%'))
  AND (:email IS NULL OR u.email = :email)
ORDER BY u.created_at DESC
LIMIT :limit OFFSET :offset
```

```java
@Repository
public interface UserRepository {
    @QueryFile("/queries/users/findWithPagination.sql")
    List<User> findWithPagination(String name, String email, int limit, int offset);
}
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 Author

**Gerard Gilabert** - [@Gergilcan](https://github.com/Gergilcan)

---

⭐ **Star this repository if you find it helpful!**
