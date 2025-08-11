# WireJ Package Scanning Configuration

When using WireJ as a dependency in your Spring Boot application, you need to configure which packages to scan for your `@RestController` interfaces that use `@ServiceClass`.

## Configuration Methods

### Method 1: Using application.properties/application.yml (Recommended)

Add the following to your `application.properties`:

```properties
# Specify packages to scan for WireJ controllers
wirej.scan.packages=com.yourcompany.controllers,com.yourcompany.api

# Optional: Enable debug logging
wirej.debug=true

# Optional: Disable auto-detection (default is true)
wirej.auto-detect-packages=false
```

Or in `application.yml`:

```yaml
wirej:
  scan:
    packages:
      - com.yourcompany.controllers
      - com.yourcompany.api
  debug: true
  auto-detect-packages: true
```

### Method 2: Using System Properties

You can also configure scanning using JVM system properties:

```bash
java -Dwirej.scan.packages=com.yourcompany.controllers,com.yourcompany.api -jar your-app.jar
```

### Method 3: Auto-Detection (Default)

If you don't specify scan packages, WireJ will try to:

1. **Auto-detect your main application package** by finding your `@SpringBootApplication` class
2. **Fall back to scanning common base packages**: `com`, `org`, `net`, `app`, `application`

The auto-detection will exclude WireJ's own packages to avoid conflicts.

## Example Usage

### 1. Create your controller interface:

```java
package com.yourcompany.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.gergilcan.wirej.annotations.ServiceClass;

@RestController
@ServiceClass(YourService.class)
public interface YourController {

    @GetMapping("/api/data")
    String getData();
}
```

### 2. Configure scanning in application.properties:

```properties
wirej.scan.packages=com.yourcompany.controllers
```

### 3. WireJ will automatically create a proxy that delegates to your service:

```java
@Service
public class YourService {
    public String getData() {
        return "Hello from service!";
    }
}
```

## Troubleshooting

### No Controllers Found

If WireJ reports "Found 0 controller candidates", check:

1. **Package configuration**: Ensure `wirej.scan.packages` includes the correct packages
2. **Annotations**: Verify your interfaces have both `@RestController` and `@ServiceClass`
3. **Enable debug logging**: Set `wirej.debug=true` to see scanning details

### Performance Considerations

- **Specific packages**: Always prefer specific package names over broad scanning
- **Avoid root packages**: Don't use packages like `com` or `org` unless necessary
- **Multiple packages**: You can specify multiple packages separated by commas

### Debug Information

Enable debug logging to see what WireJ is doing:

```properties
wirej.debug=true
logging.level.io.github.gergilcan.wirej=DEBUG
```

This will show:

- Which packages are being scanned
- How many candidates are found in each package
- Auto-detection results
- Registration details for each controller
