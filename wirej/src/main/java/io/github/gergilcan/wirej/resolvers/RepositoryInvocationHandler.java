package io.github.gergilcan.wirej.resolvers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.SQLException;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.core.RequestFilters;
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
        Integer pageNumber = getParameterValueFromName(method, args, "pageNumber");
        Integer pageSize = getParameterValueFromName(method, args, "pageSize");

        var databaseStatement = new DatabaseStatement<>(fileName, filters, pageNumber, pageSize,
                returnType.isArray() ? returnType.getComponentType() : returnType, rsqlParser, connectionHandler);

        // Set parameters for the statement
        setStatementParameters(method, args, databaseStatement, isBatch);

        Object result = null;
        if (method.getName().startsWith("get") || method.getName().startsWith("find")) {
            result = handleGetRequest(returnType, databaseStatement);
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
            // If its a batch statement, we need to set the parameters
            // we look for the parameters in the method that is an array so we know that
            // those are the ones to be iterated
            // over
            for (var arg : args) {
                if (arg.getClass().isArray()) {
                    for (Object item : (Object[]) arg) {
                        // Set parameter for each property of the item
                        setObjectFieldsToStatement(item, databaseStatement);
                        databaseStatement.addBatch();
                    }
                } else {
                    getParameterValueFromType(method, args, method.getReturnType());
                    setObjectFieldsToStatement(arg, databaseStatement);
                }
            }
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

            if (isParameterAClass(args[i])) {
                setObjectFieldsToStatement(args[i], databaseStatement);
            } else {
                setSingleParameter(methodParameters[i], args[i], databaseStatement);
            }
        }
    }

    private boolean shouldSkipParameter(String paramName) {
        return paramName.equals("filters") || paramName.equals("pageNumber") || paramName.equals("pageSize");
    }

    private boolean isParameterAClass(Object arg) {
        // Exclude also the Boolean, Integer, Long, and primitive types
        if (arg == null || arg.getClass().isPrimitive() || arg instanceof String ||
                arg instanceof Boolean || arg instanceof Integer || arg instanceof Long) {
            return false;
        }
        // Check if the argument is a class type and not an array of primitives or
        // arrays
        if (arg.getClass().isArray() && arg.getClass().getComponentType().isPrimitive()) {
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
    private <T> T getParameterValueFromName(Method method, Object[] args, String propertyName) {
        if (args != null && args.length > 1) {
            for (int i = 0; i < method.getParameters().length; i++) {
                if (method.getParameters()[i].getName().equals(propertyName)) {
                    return (T) args[i];
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getParameterValueFromType(Method method, Object[] args, Class<T> type) {
        if (args != null && args.length > 1) {
            for (int i = 0; i < method.getParameters().length; i++) {
                if (method.getParameters()[i].getType().equals(type)) {
                    return (T) args[i];
                }
            }
        }
        return null;
    }
}