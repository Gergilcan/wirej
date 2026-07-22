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
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ServiceMethod {
  /**
   * The name of the service method to call.
   * If empty, the annotated method name will be used automatically.
   */
  String value() default "";

  /**
   * When true, the annotated method's own parameter no longer identifies the
   * target service method (e.g. it's a {@code JsonNode} used to sniff a
   * single item vs. an array at runtime), so the processor resolves TWO
   * service method overloads by the same name instead of one: a single-item
   * overload and an array/batch overload. Both must exist on the service
   * class.
   */
  boolean batchSupported() default false;
}