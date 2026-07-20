package io.github.gergilcan.wirej.resolvers;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.database.ConnectionHandler;
import io.github.gergilcan.wirej.database.DatabaseStatement;
import io.github.gergilcan.wirej.exceptions.WireJException;
import io.github.gergilcan.wirej.rsql.RsqlParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepositoryInvocationHandler implements InvocationHandler {
    // Date is intentionally checked separately (via instanceof below) since it is
    // not final: java.sql.Date and java.sql.Time are also basic types.
    private static final Set<Class<?>> BASIC_TYPES = Set.of(String.class, Boolean.class, Integer.class, Long.class,
            Double.class, Float.class, Short.class, Byte.class, BigDecimal.class, Timestamp.class,
            LocalDateTime.class);

    private final ConnectionHandler connectionHandler;
    private final RsqlParser rsqlParser;

    public RepositoryInvocationHandler(ConnectionHandler connectionHandler, RsqlParser rsqlParser) {
        this.connectionHandler = connectionHandler;
        this.rsqlParser = rsqlParser;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var returnType = method.getReturnType();
        var queryFile = method.getAnnotation(QueryFile.class);
        var fileName = queryFile.value();
        var isBatch = queryFile.isBatch();

        RequestFilters filters = getParameterValueFromType(method, args, RequestFilters.class);
        RequestPagination pagination = getParameterValueFromType(method, args, RequestPagination.class);
        Class<?> entityClass = returnType.isArray() ? returnType.getComponentType()
                : getParameterValueFromType(method, args, Class.class);

        // IOException/SQLException are caught and rewrapped here because the
        // generated repository interfaces don't declare them: left unchecked, the
        // JDK proxy would swallow the real error into a message-less
        // UndeclaredThrowableException instead of surfacing what actually failed.
        DatabaseStatement<Object> databaseStatement = null;
        try {
            databaseStatement = new DatabaseStatement<>(fileName, filters, pagination,
                    entityClass, rsqlParser, connectionHandler);

            setStatementParameters(method, args, databaseStatement, isBatch);

            if (method.getName().startsWith("get") || method.getName().startsWith("find")) {
                return handleGetRequest(returnType, databaseStatement);
            } else if (method.getName().toLowerCase().contains("count")) {
                return databaseStatement.getSingleValue();
            } else if (!isBatch) {
                return returnType == Void.TYPE ? databaseStatement.execute() : databaseStatement.getResult();
            } else {
                return databaseStatement.executeBatch();
            }
        } catch (IOException | SQLException e) {
            // Query methods (getResult/execute/...) always close their own
            // connection, on success or failure. This catches failures that happen
            // *before* one of them is reached (e.g. binding parameters), where
            // nothing else would ever release the connection that was opened.
            closeQuietly(databaseStatement);
            throw new WireJException("Query failed for repository method '" + method.getName()
                    + "' (query file: " + fileName + ", entity: " + entityClass.getSimpleName() + "): "
                    + e.getMessage(), e);
        } catch (RuntimeException e) {
            closeQuietly(databaseStatement);
            throw e;
        }
    }

    private void closeQuietly(DatabaseStatement<?> databaseStatement) {
        if (databaseStatement == null) {
            return;
        }
        try {
            databaseStatement.closeStatement();
        } catch (SQLException closeException) {
            log.warn("Failed to close database statement after an earlier failure", closeException);
        }
    }

    private Object handleGetRequest(Class<?> returnType, DatabaseStatement<Object> databaseStatement)
            throws SQLException {
        if (returnType.isArray()) {
            if (!isParameterANonBasicClass(returnType.getComponentType())) {
                return databaseStatement.getSingleValueList();
            }
            return databaseStatement.getResultList();
        } else if (returnType == Long.class || returnType == Integer.class || returnType == Boolean.class) {
            return databaseStatement.getSingleValue();
        }
        return databaseStatement.getResult();
    }

    private void setStatementParameters(Method method, Object[] args, DatabaseStatement<Object> databaseStatement,
            boolean isBatch) throws SQLException {
        if (!isBatch) {
            // If its not a batch statement
            setParameters(method, args, databaseStatement);
        } else {
            setBatchParameters(method, args, databaseStatement);
        }
    }

    private void setObjectFieldsToStatement(Object item, DatabaseStatement<?> databaseStatement) {
        var fields = item.getClass().getDeclaredFields();
        for (var field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(item);
                if (field.getAnnotation(JsonAlias.class) != null) {
                    // If the field has a JsonAlias annotation, we use the first alias as the
                    // parameter name
                    String[] aliases = field.getAnnotation(JsonAlias.class).value();
                    if (aliases.length > 0) {
                        databaseStatement.setParameter(aliases[0], value);
                    }
                } else {
                    databaseStatement.setParameter(resolveParameterName(field, field.getName()), value);
                }
            } catch (IllegalAccessException e) {
                throw new WireJException("Could not access field: " + field.getName(), e);
            }
        }
    }

    private void setParameters(Method method, Object[] args, DatabaseStatement<?> databaseStatement) {
        var methodParameters = method.getParameters();
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String paramName = methodParameters[i].getName();
            if (shouldSkipParameter(paramName)) {
                continue;
            }

            if (!isParameterANonBasicClass(args[i]) || method.getName().toLowerCase().contains("count")) {
                setSingleParameter(methodParameters[i], args[i], databaseStatement);
            } else {
                setObjectFieldsToStatement(args[i], databaseStatement);
            }
        }
    }

    private void setBatchParameters(Method method, Object[] args, DatabaseStatement<?> databaseStatement)
            throws SQLException {
        var methodParameters = method.getParameters();
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String paramName = methodParameters[i].getName();
            if (shouldSkipParameter(paramName)) {
                continue;
            }
            if (args[i].getClass().isArray()) {
                // If the argument is an array, we need to iterate over the array and set the
                // parameters for each item
                for (Object item : (Object[]) args[i]) {
                    if (isParameterANonBasicClass(item)) {
                        setObjectFieldsToStatement(item, databaseStatement);
                    } else {
                        setSingleParameter(methodParameters[i], item, databaseStatement);
                    }
                    databaseStatement.addBatch();
                }
            }
        }
    }

    private boolean shouldSkipParameter(String paramName) {
        return paramName.equals("filters") || paramName.equals("pageNumber") || paramName.equals("pageSize");
    }

    private boolean isParameterANonBasicClass(Object arg) {
        if (arg == null || arg instanceof Date || BASIC_TYPES.contains(arg.getClass())) {
            return false;
        }
        // Check if the argument is a class type and not an array of primitives or
        // arrays
        if (arg.getClass().isArray() && (arg.getClass().getComponentType().isPrimitive()
                || arg.getClass().getComponentType() == String.class)) {
            return false;
        }

        return true;
    }

    private void setSingleParameter(java.lang.reflect.Parameter parameter, Object arg,
            DatabaseStatement<?> databaseStatement) {
        databaseStatement.setParameter(resolveParameterName(parameter, parameter.getName()), arg);
    }

    /**
     * Resolves the parameter name to use: the first @JsonAlias value if present,
     * otherwise the snake_case form of the raw name.
     */
    private String resolveParameterName(AnnotatedElement element, String rawName) {
        JsonAlias aliasAnnotation = element.getAnnotation(JsonAlias.class);
        if (aliasAnnotation != null && aliasAnnotation.value().length > 0) {
            return aliasAnnotation.value()[0];
        }
        return rawName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private <T> T getParameterValueFromType(Method method, Object[] args, Class<T> type) {
        if (args != null && args.length > 0) {
            for (int i = 0; i < method.getParameters().length; i++) {
                if (method.getParameters()[i].getType().equals(type)) {
                    return (T) args[i];
                }
            }
        }

        // If there is no class defined use the return type of the array component type
        if (type == Class.class) {
            return (T) method.getReturnType();
        }

        return null;
    }
}