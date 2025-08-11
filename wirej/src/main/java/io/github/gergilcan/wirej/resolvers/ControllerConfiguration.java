package io.github.gergilcan.wirej.resolvers;

import java.util.Set;
import java.util.Arrays;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.config.WireJConfiguration;
import lombok.extern.slf4j.Slf4j;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WireJConfiguration.class)
@Slf4j
public class ControllerConfiguration implements BeanDefinitionRegistryPostProcessor {

    private static final String[] DEFAULT_SCAN_PACKAGES = { "com", "org", "net", "io", "app", "application" };

    private final WireJConfiguration wireJConfig;

    public ControllerConfiguration() {
        this.wireJConfig = new WireJConfiguration();
    }

    public ControllerConfiguration(WireJConfiguration wireJConfig) {
        this.wireJConfig = wireJConfig != null ? wireJConfig : new WireJConfiguration();
    }

    /**
     * Detects the main application package by finding the class
     * with @SpringBootApplication
     * or falls back to scanning common base packages
     */
    private String[] detectApplicationPackages() {
        try {
            // First priority: Use explicitly configured packages
            if (wireJConfig.getScanPackages() != null && wireJConfig.getScanPackages().length > 0) {
                log.info("Using explicitly configured scan packages: {}",
                        Arrays.toString(wireJConfig.getScanPackages()));
                return wireJConfig.getScanPackages();
            }

            // Skip auto-detection if disabled
            if (!wireJConfig.isAutoDetectPackages()) {
                log.warn("Auto-detection disabled but no scan packages configured, using fallback packages");
                return DEFAULT_SCAN_PACKAGES;
            }

            // Second priority: System property for backward compatibility
            String configuredPackage = System.getProperty("wirej.scan.packages");
            if (configuredPackage != null && !configuredPackage.trim().isEmpty()) {
                String[] packages = configuredPackage.split(",");
                for (int i = 0; i < packages.length; i++) {
                    packages[i] = packages[i].trim();
                }
                log.info("Using system property scan packages: {}", Arrays.toString(packages));
                return packages;
            }

            // Third priority: Try to find Spring Boot main application class
            String mainClass = System.getProperty("sun.java.command");
            if (mainClass != null && !mainClass.isEmpty()) {
                // Extract just the class name, ignore arguments
                String className = mainClass.split(" ")[0];
                String packageName = getApplicationPackageFromClass(className);
                if (packageName != null) {
                    log.info("Detected application package from main class: {}", packageName);
                    return new String[] { packageName };
                }
            }

            // Fallback: Use common base packages but exclude the library package
            log.warn("Could not detect specific application package, using broad scan (excluding library packages)");
            return DEFAULT_SCAN_PACKAGES;

        } catch (Exception e) {
            log.warn("Error detecting application packages, using fallback", e);
            return DEFAULT_SCAN_PACKAGES;
        }
    }

    private String getApplicationPackageFromClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (clazz.isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class)) {
                return clazz.getPackage().getName();
            }
        } catch (ClassNotFoundException e) {
            log.debug("Could not load main class: {}", className);
        }
        return null;
    }

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

        // Detect application packages dynamically
        String[] packagesToScan = detectApplicationPackages();
        log.info("Scanning packages for WireJ controllers: {}", Arrays.toString(packagesToScan));

        Set<BeanDefinition> candidates = new java.util.HashSet<>();
        for (String packageToScan : packagesToScan) {
            Set<BeanDefinition> packageCandidates = scanner.findCandidateComponents(packageToScan);
            candidates.addAll(packageCandidates);
            log.debug("Found {} candidates in package: {}", packageCandidates.size(), packageToScan);
        }
        log.info("Found {} total controller candidates across all packages", candidates.size());
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
