package io.github.gergilcan.wirej.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the service class that contains the implementation
 * methods for a controller interface. This is used in conjunction with
 * 
 * @ServiceMethod to create proxied controllers.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceClass {
    /**
     * The service class that contains the implementation methods.
     * 
     * @return the service class
     */
    Class<?> value();
}
