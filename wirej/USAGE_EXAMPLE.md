# WireJ Auto-Configuration Example

With WireJ's auto-configuration, you can get started with minimal setup!

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.gergilcan</groupId>
    <artifactId>wirej</artifactId>
    <version>0.0.3.2</version>
</dependency>
```

### 2. Create your repositories (optional)

```java
@Repository
public interface UserRepository {
    @QueryFile("/queries/User/findById.sql")
    User findById(Long id);

    @QueryFile("/queries/User/create.sql")
    void create(User user);
}
```

### 3. Create your services

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User findById(Long id) {
        return userRepository.findById(id);
    }

    public void createUser(User user) {
        userRepository.create(user);
    }
}
```

### 4. Create your controller interfaces

```java
@RestController
@RequestMapping("/users")
@ServiceClass(UserService.class)
public interface UserController {

    @GetMapping("/{id}")
    @ServiceMethod  // Automatically calls findById
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> findById(@PathVariable Long id);

    @PostMapping("/create")
    @ServiceMethod("createUser")  // Calls createUser method
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> createUser(@RequestBody User user);
}
```

### 5. Add your SQL files

Create `/src/main/resources/queries/User/findById.sql`:

```sql
SELECT * FROM users WHERE id = :id
```

Create `/src/main/resources/queries/User/create.sql`:

```sql
INSERT INTO users (id, name) VALUES (:id, :name)
```

## That's it!

WireJ will automatically:

✅ **Scan for repositories** with `@Repository` and `@QueryFile` annotations  
✅ **Create repository proxies** that execute your SQL files  
✅ **Scan for controllers** with `@RestController` and `@ServiceClass` annotations  
✅ **Create controller proxies** that delegate to your services  
✅ **Wire everything together** with proper Spring configuration  
✅ **Handle ResponseEntity creation** with the correct status codes

No manual configuration needed! Just add the dependency and start coding.

## Advanced Features

### Custom HTTP Status Codes

```java
@PostMapping("/users")
@ServiceMethod("createUser")
@ResponseStatus(HttpStatus.CREATED)  // Returns 201 instead of 200
ResponseEntity<?> createUser(@RequestBody User user);
```

### Method Name Mapping

```java
@GetMapping("/users")
@ServiceMethod("getAllUsers")  // Maps to different service method name
ResponseEntity<?> getUsers();
```

### Void Service Methods

```java
@DeleteMapping("/{id}")
@ServiceMethod("deleteUser")
@ResponseStatus(HttpStatus.NO_CONTENT)  // Returns 204 with no body
ResponseEntity<?> deleteUser(@PathVariable Long id);
```
