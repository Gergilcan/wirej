package io.github.gergilcan.wirej.resolvers;

import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class ControllerConfiguration implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                // First check basic requirements
                if (!beanDefinition.getMetadata().isInterface()) {
                    return false;
                }
                if (!beanDefinition.getMetadata().hasAnnotation(RestController.class.getName())) {
                    return false;
                }

                // Try multiple ways to detect @ServiceClass annotation
                try {
                    Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());

                    // Check all available annotations
                    log.debug("Available annotations on {}: {}", clazz.getName(),
                            java.util.Arrays.toString(clazz.getAnnotations()));
                    log.debug("Available declared annotations on {}: {}", clazz.getName(),
                            java.util.Arrays.toString(clazz.getDeclaredAnnotations()));

                    // Check annotation names
                    boolean hasServiceClass = java.util.Arrays.stream(clazz.getDeclaredAnnotations())
                            .anyMatch(ann -> ann instanceof io.github.gergilcan.wirej.annotations.ServiceClass);

                    log.debug(
                            "Checking candidate: {} - Interface: {}, RestController: {}, ServiceClass: {} (direct check)",
                            beanDefinition.getBeanClassName(),
                            beanDefinition.getMetadata().isInterface(),
                            beanDefinition.getMetadata().hasAnnotation(RestController.class.getName()),
                            hasServiceClass);

                    return hasServiceClass;
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load class for annotation check: {}", beanDefinition.getBeanClassName());
                    return false;
                }
            }
        };

        // Only add RestController filter since we need both annotations
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents("io.github.gergilcan.wirej");
        log.info("Found {} controller candidates", candidates.size());
        for (BeanDefinition candidate : candidates) {
            try {

                Class<?> controllerInterface = Class.forName(candidate.getBeanClassName());
                ServiceClass serviceClassAnnotation = controllerInterface.getAnnotation(ServiceClass.class);

                if (serviceClassAnnotation == null) {
                    log.warn("Controller interface {} does not have @ServiceClass annotation",
                            controllerInterface.getName());
                    continue;
                }

                log.info("Processing controller interface: {}", controllerInterface.getName());
                Class<?> serviceClass = serviceClassAnnotation.value();
                String beanName = controllerInterface.getSimpleName();
                beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);

                BeanDefinitionBuilder builder = BeanDefinitionBuilder
                        .genericBeanDefinition(ControllerProxyFactoryBean.class);
                builder.addConstructorArgValue(controllerInterface);
                builder.addConstructorArgValue(serviceClass);

                // Ensure the bean definition has the correct type
                BeanDefinition beanDefinition = builder.getBeanDefinition();
                beanDefinition.setAttribute("factoryBeanObjectType", controllerInterface);

                registry.registerBeanDefinition(beanName, beanDefinition);
                log.debug("Registered controller proxy: {} for interface: {} with service: {}",
                        beanName, controllerInterface.getName(), serviceClass.getName());
            } catch (ClassNotFoundException e) {
                log.error("Could not load class: {}", candidate.getBeanClassName(), e);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No post-processing needed here
    }

    public static class ControllerProxyFactoryBean implements FactoryBean<Object>, ApplicationContextAware {
        private final Class<?> controllerInterface;
        private final Class<?> serviceClass;
        private ApplicationContext applicationContext;

        public ControllerProxyFactoryBean(Class<?> controllerInterface, Class<?> serviceClass) {
            this.controllerInterface = controllerInterface;
            this.serviceClass = serviceClass;
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
        }

        @Override
        public Object getObject() {
            return java.lang.reflect.Proxy.newProxyInstance(
                    controllerInterface.getClassLoader(),
                    new Class<?>[] { controllerInterface },
                    new ControllerInvocationHandler(applicationContext, serviceClass));
        }

        @Override
        public Class<?> getObjectType() {
            return controllerInterface;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
