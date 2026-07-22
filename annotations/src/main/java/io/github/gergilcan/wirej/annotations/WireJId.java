package io.github.gergilcan.wirej.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the primary key field of a WireJ entity used with
 * {@code StandardRepository}. Only needed when the entity doesn't already
 * carry {@code jakarta.persistence.Id} (which WireJ also reads, without
 * requiring the JPA dependency) and the field isn't simply named {@code id}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface WireJId {
}
