package com.gergilcan.wirej.resolvers;

import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;

import com.gergilcan.wirej.database.ConnectionHandler;
import com.gergilcan.wirej.rsql.RsqlParser;

import lombok.extern.slf4j.Slf4j;

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
                log.debug(
                        "Registered repository: " + beanName + " for interface: " + repositoryInterface.getName());
            } catch (ClassNotFoundException e) {
                log.error("Could not load class: " + candidate.getBeanClassName(), e);
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
}
