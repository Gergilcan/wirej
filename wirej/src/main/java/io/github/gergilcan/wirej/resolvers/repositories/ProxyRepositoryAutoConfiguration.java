package io.github.gergilcan.wirej.resolvers.repositories;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;

import io.github.gergilcan.wirej.rsql.RsqlParser;
import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@Slf4j
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
    public RsqlParser rsqlParser() {
        return new RsqlParser();
    }

    /**
     * This class scans for interfaces annotated with @Repository and registers
     * a proxy factory bean for each one.
     */
    static class ProxyRepositoryRegistrar implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            // FIX: Use Spring's utility to find the base package of the main application
            // class.
            // This is the key change to make your library work for its users.
            List<String> basePackages = AutoConfigurationPackages.get((BeanFactory) registry);
            if (basePackages == null || basePackages.isEmpty()) {
                log.warn("Could not determine base packages for repository scanning.");
                return;
            }

            log.info("Scanning for @Repository interfaces in packages: {}", basePackages);

            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                    false) {
                @Override
                protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                    return beanDefinition.getMetadata().isInterface() &&
                            !beanDefinition.getMetadata().isAnnotation();
                }
            };
            scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));

            for (String basePackage : basePackages) {
                Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
                for (BeanDefinition candidate : candidates) {
                    try {
                        Class<?> repositoryInterface = Class.forName(candidate.getBeanClassName());
                        String beanName = repositoryInterface.getSimpleName();
                        beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);

                        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                                .genericBeanDefinition(RepositoryProxyFactoryBean.class);
                        builder.addConstructorArgValue(repositoryInterface);
                        // These dependencies will be autowired by Spring into the factory bean.
                        // Ensure beans named 'dataSource' and 'rsqlParser' exist in the context.
                        builder.addConstructorArgReference("dataSource");
                        builder.addConstructorArgReference("rsqlParser");

                        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
                        log.debug("Registered repository proxy: {} for interface: {}", beanName,
                                repositoryInterface.getName());

                    } catch (ClassNotFoundException e) {
                        log.error("Could not load class for repository proxy: {}", candidate.getBeanClassName(), e);
                    }
                }
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // No-op
        }
    }
}

class RepositoryProxyFactoryBean implements FactoryBean<Object> {
    private final Class<?> repositoryInterface;
    private final DataSource dataSource;
    private final RsqlParser rsqlParser;

    public RepositoryProxyFactoryBean(Class<?> repositoryInterface, DataSource dataSource,
            RsqlParser rsqlParser) {
        this.repositoryInterface = repositoryInterface;
        this.dataSource = dataSource;
        this.rsqlParser = rsqlParser;
    }

    @Override
    public Object getObject() {
        RepositoryInvocationHandler handler = new RepositoryInvocationHandler(
                dataSource,
                rsqlParser,
                repositoryInterface); // Pass the interface to extract generic info

        // Get all interfaces in the hierarchy
        Set<Class<?>> allInterfaces = getAllInterfaces(repositoryInterface);

        return Proxy.newProxyInstance(
                repositoryInterface.getClassLoader(),
                allInterfaces.toArray(new Class<?>[0]),
                handler);
    }

    private Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new HashSet<>();
        if (clazz.isInterface()) {
            interfaces.add(clazz);
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            interfaces.addAll(getAllInterfaces(iface));
        }
        return interfaces;
    }

    @Override
    public Class<?> getObjectType() {
        return repositoryInterface;
    }
}