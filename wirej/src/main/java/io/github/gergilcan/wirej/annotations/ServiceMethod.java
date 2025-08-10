package io.github.gergilcan.wirej.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method in a controller interface that should be proxied
 * to a service method. The proxy will call the specified method on the service
 * class defined by @ServiceClass.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceMethod {
    /**
     * The name of the method to call on the service class.
     * If not specified, uses the annotated method's name.
     * 
     * @return the service method name
     */
    String value() default "";
}
