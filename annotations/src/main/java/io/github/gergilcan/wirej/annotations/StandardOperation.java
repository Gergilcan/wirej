package io.github.gergilcan.wirej.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code StandardRepository} method with the CRUD operation whose SQL
 * the annotation processor should generate from the entity's own fields.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface StandardOperation {
  StandardOperationType value();
}
