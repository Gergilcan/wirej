package io.github.gergilcan.wirej.resolvers;

import java.util.List;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import io.github.gergilcan.wirej.annotations.ServiceClass;

@Configuration(proxyBeanMethods = false)
public class ProxyControllerAutoConfiguration {
    private ProxyControllerAutoConfiguration() {
        // Private constructor to prevent instantiation
    }

    /**
     * This method registers our custom registrar.
     * It must be static because BeanDefinitionRegistryPostProcessor runs very early
     * in the startup process, before the @Configuration class itself is
     * instantiated.
     */
    @Bean
    public static ProxyControllerRegistrar proxyControllerRegistrar() {
        return new ProxyControllerRegistrar();
    }

    /**
     * This static inner class is the core of the auto-configuration.
     * It implements BeanDefinitionRegistryPostProcessor to scan for and register
     * our proxy controllers after the application's beans have been defined.
     */
    static class ProxyControllerRegistrar implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            // Use Spring's utility to find the base package of the main application class.
            // This removes the need for the user to specify it manually.
            List<String> basePackages = AutoConfigurationPackages.get((BeanFactory) registry);

            if (basePackages.isEmpty()) {
                return;
            }

            // Create a scanner to find our target interfaces
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                    false) {
                @Override
                protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                    // We are only interested in interfaces
                    return beanDefinition.getMetadata().isInterface();
                }
            };
            // The interfaces must be annotated with @RestController and our custom
            // @ServiceClass
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            scanner.addIncludeFilter(new AnnotationTypeFilter(ServiceClass.class));

            // Scan all discovered base packages
            for (String basePackage : basePackages) {
                Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(basePackage);
                for (BeanDefinition candidate : candidateComponents) {
                    try {
                        // Create a bean definition for our proxy factory
                        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                                .genericBeanDefinition(ServiceProxyFactoryBean.class);

                        // The factory needs to know which interface it is creating a proxy for.
                        String interfaceName = candidate.getBeanClassName();
                        builder.addConstructorArgValue(Class.forName(interfaceName));

                        // Register the factory bean with Spring
                        String beanName = StringUtils.uncapitalize(Class.forName(interfaceName).getSimpleName());
                        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());

                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(
                                "Could not find class for bean definition: " + candidate.getBeanClassName(), e);
                    }
                }
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // We don't need to do anything here.
        }
    }
}