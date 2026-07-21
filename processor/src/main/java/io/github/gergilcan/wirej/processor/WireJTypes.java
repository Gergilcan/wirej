package io.github.gergilcan.wirej.processor;

import java.io.IOException;
import java.sql.SQLException;

import com.squareup.javapoet.ClassName;

/**
 * Fully-qualified references to runtime types the generated code calls into.
 * These live in the {@code wirej} module, which the {@code processor} module
 * cannot depend on (it would create a circular module dependency, since
 * {@code wirej} depends on {@code processor} as an annotation processor
 * path). Referencing them as string-based {@link ClassName}s instead of
 * {@code .class} literals lets javapoet emit correct source without ever
 * loading those classes.
 */
final class WireJTypes {
    static final ClassName DATABASE_STATEMENT = ClassName.get("io.github.gergilcan.wirej.database",
            "DatabaseStatement");
    static final ClassName CONNECTION_HANDLER = ClassName.get("io.github.gergilcan.wirej.database",
            "ConnectionHandler");
    static final ClassName PARAMETER_BINDER = ClassName.get("io.github.gergilcan.wirej.database", "ParameterBinder");
    static final ClassName RSQL_PARSER = ClassName.get("io.github.gergilcan.wirej.rsql", "RsqlParser");
    static final ClassName WIREJ_EXCEPTION = ClassName.get("io.github.gergilcan.wirej.exceptions", "WireJException");

    static final ClassName RESPONSE_ENTITY = ClassName.get("org.springframework.http", "ResponseEntity");
    static final ClassName HTTP_STATUS = ClassName.get("org.springframework.http", "HttpStatus");

    static final ClassName IO_EXCEPTION = ClassName.get(IOException.class);
    static final ClassName SQL_EXCEPTION = ClassName.get(SQLException.class);
    static final ClassName RUNTIME_EXCEPTION = ClassName.get(RuntimeException.class);

    private WireJTypes() {
    }
}
