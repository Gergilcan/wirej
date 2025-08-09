package io.github.gergilcan.wirej.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to link a controller method to a service method by name.
 * If no value is provided, the annotated method name will be used
 * automatically.
 */
// Add this line to ensure the annotation is available at compile time.
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface ServiceMethod {
  /**
   * The name of the service method to call.
   * If empty, the annotated method name will be used automatically.
   */
  String value() default "";
}