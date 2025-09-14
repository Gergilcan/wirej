package io.github.gergilcan.wirej.resolvers.repositories;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.database.DatabaseStatement;
import io.github.gergilcan.wirej.rsql.RsqlParser;

public class RepositoryInvocationHandler implements InvocationHandler {
    private final DataSource dataSource;
    private final RsqlParser rsqlParser;
    private final Class<?> entityClass;

    public RepositoryInvocationHandler(DataSource dataSource, RsqlParser rsqlParser, Class<?> repositoryInterface) {
        this.dataSource = dataSource;
        this.rsqlParser = rsqlParser;
        this.entityClass = extractEntityClass(repositoryInterface);
    }

    private Class<?> extractEntityClass(Class<?> repositoryInterface) {
        // Look for generic interfaces like CrudRepository<User>
        Type[] genericInterfaces = repositoryInterface.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType paramType) {
                Type[] actualTypes = paramType.getActualTypeArguments();
                if (actualTypes.length > 0 && actualTypes[0] instanceof Class) {
                    return (Class<?>) actualTypes[0];
                }
            }
        }
        return Object.class; // Fallback if not found
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Empty implementation - you handle it yourself
        // dataSource and rsqlParser are available here
        var returnType = method.getReturnType();
        Object result = null;
        QueryFile queryFile = method.getAnnotation(QueryFile.class);
        if (queryFile != null) {
            var fileName = method.getAnnotation(QueryFile.class).value().replace("{entity}",
                    entityClass.getSimpleName());
            var isBatch = method.getAnnotation(QueryFile.class).isBatch();

            RequestFilters filters = getParameterValueFromType(method, args, RequestFilters.class);
            RequestPagination pagination = getParameterValueFromType(method, args, RequestPagination.class);
            try (var databaseStatement = new DatabaseStatement<>(fileName, filters, pagination,
                    entityClass, rsqlParser, dataSource)) {

                // Set parameters for the statement
                setStatementParameters(method, args, databaseStatement, isBatch);

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
            }
        }
        return result;
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
            if (shouldSkipParameter(methodParameters[i])) {
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
            if (shouldSkipParameter(methodParameters[i])) {
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

    private boolean shouldSkipParameter(Parameter parameter) {
        return parameter.getType() == RequestFilters.class || parameter.getType() == RequestPagination.class;
    }

    private boolean isParameterANonBasicClass(Object arg) {
        // Exclude also the Boolean, Integer, Long, and primitive types
        if (arg == null || arg.getClass().isPrimitive() || arg == String.class || arg == Boolean.class
                || arg == Integer.class
                || arg == Long.class || arg == Double.class || arg == Float.class
                || arg == Short.class
                || arg == Byte.class || arg == BigDecimal.class || arg == Timestamp.class
                || arg == java.security.Timestamp.class || arg == java.util.Date.class
                || arg == LocalDateTime.class || arg instanceof String ||
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