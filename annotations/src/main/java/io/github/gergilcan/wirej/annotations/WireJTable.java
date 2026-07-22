package io.github.gergilcan.wirej.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the database table a WireJ entity maps to, used by the annotation
 * processor to generate SQL for {@code StandardRepository} operations. Only
 * needed when the entity doesn't already carry
 * {@code jakarta.persistence.Table(name = ...)} (which WireJ also reads,
 * without requiring the JPA dependency); when both are present, this one
 * wins. Deliberately named differently from the JPA annotation so both can
 * coexist on the same entity class without import ambiguity.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WireJTable {
  String value();
}
