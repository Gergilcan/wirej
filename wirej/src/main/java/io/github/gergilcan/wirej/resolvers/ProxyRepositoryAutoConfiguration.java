package io.github.gergilcan.wirej.resolvers;

import java.lang.reflect.Proxy;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Repository;

import io.github.gergilcan.wirej.database.ConnectionHandler;
import io.github.gergilcan.wirej.rsql.RsqlParser;

@Configuration(proxyBeanMethods = false)
public class ProxyRepositoryAutoConfiguration {
    private ProxyRepositoryAutoConfiguration() {
        // Private constructor to prevent instantiation
    }

    /**
     * This method registers our custom registrar. It's static to ensure it runs
     * early in the Spring application lifecycle.
     */
    @Bean
    public static ProxyRepositoryRegistrar proxyRepositoryRegistrar() {
        return new ProxyRepositoryRegistrar();
    }

    @Bean
    public ConnectionHandler connectionHandler(DataSource dataSource) {
        return new ConnectionHandler(dataSource);
    }

    @Bean
    public RsqlParser rsqlParser() {
        return new RsqlParser();
    }

    /**
     * Scans for interfaces annotated with @Repository and registers a proxy
     * factory bean for each one.
     */
    static class ProxyRepositoryRegistrar extends AnnotatedInterfaceRegistrar
            implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            scanAndRegister(registry, Repository.class);
        }

        @Override
        protected void registerBean(BeanDefinitionRegistry registry, String beanName, Class<?> repositoryInterface) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                    .genericBeanDefinition(RepositoryProxyFactoryBean.class);
            builder.addConstructorArgValue(repositoryInterface);
            // These dependencies will be autowired by Spring into the factory bean.
            // Ensure beans named 'connectionHandler' and 'rsqlParser' exist in the context.
            builder.addConstructorArgReference("connectionHandler");
            builder.addConstructorArgReference("rsqlParser");

            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // No-op
        }
    }
}

class RepositoryProxyFactoryBean implements FactoryBean<Object> {
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
        // The InvocationHandler contains the logic that runs when a repository method
        // is called.
        RepositoryInvocationHandler handler = new RepositoryInvocationHandler(
                connectionHandler,
                rsqlParser);
        return Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                new Class<?>[] { repositoryInterface },
                handler);
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }
}
