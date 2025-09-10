package io.github.gergilcan.wirej.resolvers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.database.ConnectionHandler;
import io.github.gergilcan.wirej.database.DatabaseStatement;
import io.github.gergilcan.wirej.rsql.RsqlParser;

public class RepositoryInvocationHandler implements InvocationHandler {
    private final ConnectionHandler connectionHandler;
    private final RsqlParser rsqlParser;

    public RepositoryInvocationHandler(ConnectionHandler connectionHandler, RsqlParser rsqlParser) {
        this.connectionHandler = connectionHandler;
        this.rsqlParser = rsqlParser;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Empty implementation - you handle it yourself
        // connectionHandler and rsqlParser are available here
        var returnType = method.getReturnType();
        var fileName = method.getAnnotation(QueryFile.class).value();
        var isBatch = method.getAnnotation(QueryFile.class).isBatch();

        RequestFilters filters = getParameterValueFromType(method, args, RequestFilters.class);
        RequestPagination pagination = getParameterValueFromType(method, args, RequestPagination.class);
        Class<?> entityClass = returnType.isArray() ? returnType.getComponentType()
                : getParameterValueFromType(method, args, Class.class);
        var databaseStatement = new DatabaseStatement<>(fileName, filters, pagination,
                entityClass, rsqlParser, connectionHandler);

        // Set parameters for the statement
        setStatementParameters(method, args, databaseStatement, isBatch);

        Object result = null;
        if (method.getName().startsWith("get") || method.getName().startsWith("find")) {
            result = handleGetRequest(returnType, databaseStatement);
        } else if (method.getName().toLowerCase().contains("count")) {
            result = databaseStatement.getSingleValue();
        } else {
            // Execute the statement and return the result if it's not a void method
            if (!isBatch) {
                result = method.getReturnType() == Void.TYPE ? databaseStatement.execute()
                        : databaseStatement.getResult();
            } else {
                result = databaseStatement.executeBatch();
            }
        }
        return result;
    }

    private Object handleGetRequest(Class<?> returnType, DatabaseStatement<Object> databaseStatement)
            throws SQLException {
        if (returnType.isArray()) {
            if (!isParameterAClass(returnType.getComponentType())) {
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
                    // Transform field name to snake_case
                    String paramName = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
                    databaseStatement.setParameter(paramName, value);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not access field: " + field.getName(), e);
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

            if (!isParameterAClass(args[i]) || method.getName().toLowerCase().contains("count")) {
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
                    if (isParameterAClass(item)) {
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
        // Exclude also the Boolean, Integer, Long, and primitive types
        var argClass = arg.getClass();
        if (argClass.isPrimitive() || argClass == String.class || argClass == Boolean.class || argClass == Integer.class
                || argClass == Long.class || argClass == Double.class || argClass == Float.class
                || argClass == Short.class
                || argClass == Byte.class || argClass == BigDecimal.class || argClass == Timestamp.class
                || argClass == java.security.Timestamp.class || argClass == java.util.Date.class
                || argClass == LocalDateTime.class || arg instanceof String ||
                arg instanceof Boolean || arg instanceof Integer || arg instanceof Long || arg instanceof Double
                || arg instanceof Float || arg instanceof Short || arg instanceof Byte || arg instanceof BigDecimal
                || arg instanceof Timestamp || arg instanceof java.security.Timestamp || arg instanceof java.util.Date
                || arg instanceof LocalDateTime) {
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
        JsonAlias aliasAnnotation = parameter.getAnnotation(JsonAlias.class);
        if (aliasAnnotation != null && aliasAnnotation.value().length > 0) {
            databaseStatement.setParameter(aliasAnnotation.value()[0], arg);
        } else {
            String paramName = parameter.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            databaseStatement.setParameter(paramName, arg);
        }
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