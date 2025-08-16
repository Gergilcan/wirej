package io.github.gergilcan.wirej.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the repository class that contains the implementation
 * methods for a controller interface. This allows the controller proxy to
 * delegate directly to repository methods instead of service methods.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepositoryClass {
    /**
     * The repository class that contains the implementation methods.
     * 
     * @return the repository class
     */
    Class<?> value();
}
