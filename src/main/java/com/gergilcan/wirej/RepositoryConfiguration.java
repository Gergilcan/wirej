package com.gergilcan.wirej;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.gergilcan.wirej.annotations.QueryFile;
import com.gergilcan.wirej.core.RequestFilters;
import com.gergilcan.wirej.database.ConnectionHandler;
import com.gergilcan.wirej.database.DatabaseStatement;
import com.gergilcan.wirej.rsql.RsqlParser;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class RepositoryConfiguration implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface()
                        && beanDefinition.getMetadata().hasAnnotation(Repository.class.getName());
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents("com.gergilcan.wirej");

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> repositoryInterface = Class.forName(candidate.getBeanClassName());
                String beanName = repositoryInterface.getSimpleName();
                beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);

                BeanDefinitionBuilder builder = BeanDefinitionBuilder
                        .genericBeanDefinition(RepositoryProxyFactoryBean.class);
                builder.addConstructorArgValue(repositoryInterface);
                builder.addConstructorArgReference("connectionHandler");
                builder.addConstructorArgReference("rsqlParser");

                // Ensure the bean definition has the correct type
                BeanDefinition beanDefinition = builder.getBeanDefinition();
                beanDefinition.setAttribute("factoryBeanObjectType", repositoryInterface);

                registry.registerBeanDefinition(beanName, beanDefinition);
                logger.debug(
                        "Registered repository: " + beanName + " for interface: " + repositoryInterface.getName());
            } catch (ClassNotFoundException e) {
                logger.error("Could not load class: " + candidate.getBeanClassName(), e);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No post-processing needed here
    }

    public static class RepositoryProxyFactoryBean implements FactoryBean<Object> {
        private final Class<?> repositoryInterface;
        private final ConnectionHandler connectionHandler;
        private final RsqlParser rsqlParser;

        public RepositoryProxyFactoryBean(Class<?> repositoryInterface, ConnectionHandler connectionHandler,
                RsqlParser rsqlParser) {
            this.repositoryInterface = repositoryInterface;
            this.connectionHandler = connectionHandler;

            this.rsqlParser = rsqlParser;
        }

        @Override
        public Object getObject() {
            return java.lang.reflect.Proxy.newProxyInstance(repositoryInterface.getClassLoader(),
                    new Class<?>[] { repositoryInterface },
                    new RepositoryInvocationHandler(connectionHandler, rsqlParser));
        }

        @Override
        public Class<?> getObjectType() {
            return repositoryInterface;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }

    public static class RepositoryInvocationHandler implements InvocationHandler {
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

            if (method.getName().startsWith("get")) {
                return handleGetRequest(returnType, databaseStatement);
            } else {
                // Execute the statement and return the result if it's not a void method
                if (!isBatch) {
                    return method.getReturnType() == Void.TYPE ? databaseStatement.execute()
                            : databaseStatement.getResult();
                }
                return databaseStatement.executeBatch();
            }
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
                            setObjectFieldsToStatement(DatabaseStatement, item);
                            DatabaseStatement.addBatch();
                        }
                    } else {
                        getParameterValueFromType(method, args, method.getReturnType());
                        setObjectFieldsToStatement(DatabaseStatement, arg);
                    }
                }
            }
        }

        private void setObjectFieldsToStatement(Object item) {
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
                            DatabaseStatement.setParameter(aliases[0], value);
                        }
                    } else {
                        // Transform field name to snake_case
                        String paramName = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
                        DatabaseStatement.setParameter(paramName, value);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Could not access field: " + field.getName(), e);
                }
            }
        }

        private void setParameters(Method method, Object[] args) {
            var methodParameters = method.getParameters();
            if (args == null) {
                return;
            }
            for (int i = 0; i < args.length; i++) {
                String paramName = methodParameters[i].getName();
                if (shouldSkipParameter(paramName)) {
                    continue;
                }
                if (isReturnTypeArgument(args[i], method)) {
                    setObjectFieldsToStatement(DatabaseStatement, args[i]);
                } else {
                    setSingleParameter(methodParameters[i], args[i]);
                }
            }
        }

        private boolean shouldSkipParameter(String paramName) {
            return paramName.equals("filters") || paramName.equals("pageNumber") || paramName.equals("pageSize");
        }

        private boolean isReturnTypeArgument(Object arg, Method method) {
            return arg != null && arg.getClass() == method.getReturnType();
        }

        private void setSingleParameter(java.lang.reflect.Parameter parameter, Object arg) {
            JsonAlias aliasAnnotation = parameter.getAnnotation(JsonAlias.class);
            if (aliasAnnotation != null && aliasAnnotation.value().length > 0) {
                DatabaseStatement.setParameter(aliasAnnotation.value()[0], arg);
            } else {
                String paramName = parameter.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
                DatabaseStatement.setParameter(paramName, arg);
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
}
