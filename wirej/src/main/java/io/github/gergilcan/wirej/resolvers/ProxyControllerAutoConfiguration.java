package io.github.gergilcan.wirej.resolvers;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.github.gergilcan.wirej.annotations.ServiceClass;

/**
 * This is the entry point for the auto-configuration.
 * It now implements ApplicationListener<ContextRefreshedEvent> to solve the 404
 * issue
 * by manually registering the request mappings after the context is
 * initialized.
 */
@Configuration(proxyBeanMethods = false)
public class ProxyControllerAutoConfiguration
        implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(ProxyControllerAutoConfiguration.class);
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * This is the FIX for the 404 and Ambiguous Mapping errors.
     * This method is called when the application context is fully initialized.
     * We find our proxy controllers and manually register their request mappings.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Get the handler mapping bean that manages all @RequestMapping endpoints
        RequestMappingHandlerMapping handlerMapping = applicationContext.getBean(RequestMappingHandlerMapping.class);

        // Find all beans that were created from interfaces annotated with @ServiceClass
        Map<String, Object> proxyControllers = applicationContext.getBeansWithAnnotation(ServiceClass.class);

        log.info("Found {} proxy controllers to register.", proxyControllers.size());

        proxyControllers.forEach((beanName, beanInstance) -> {
            // The bean is a JDK Proxy. We need to find the original interface.
            Class<?> userFacingInterface = ClassUtils.getUserClass(beanInstance);
            log.debug("Processing proxy controller: {}", userFacingInterface.getName());

            // Get the class-level @RequestMapping to use as a path prefix
            RequestMapping typeRequestMapping = AnnotatedElementUtils.findMergedAnnotation(userFacingInterface,
                    RequestMapping.class);
            RequestMappingInfo typeMappingInfo = (typeRequestMapping != null)
                    ? RequestMappingInfo.paths(typeRequestMapping.path()).build()
                    : RequestMappingInfo.paths("").build();

            // Iterate over all methods in the interface
            for (Method method : userFacingInterface.getMethods()) {
                // Check if the method is annotated with @RequestMapping or a derivative
                // (@GetMapping, etc.)
                RequestMapping methodRequestMapping = AnnotatedElementUtils.findMergedAnnotation(method,
                        RequestMapping.class);
                if (methodRequestMapping != null) {

                    // Create the method-level mapping info
                    RequestMappingInfo methodMappingInfo = RequestMappingInfo
                            .paths(methodRequestMapping.path())
                            .methods(methodRequestMapping.method())
                            .params(methodRequestMapping.params())
                            .headers(methodRequestMapping.headers())
                            .consumes(methodRequestMapping.consumes())
                            .produces(methodRequestMapping.produces())
                            .build();

                    // Combine the class-level and method-level mappings
                    RequestMappingInfo combinedMappingInfo = typeMappingInfo.combine(methodMappingInfo);

                    // FIX: Unregister the mapping first to avoid ambiguity.
                    // This removes any mapping that Spring's initial scan might have created for
                    // the interface.
                    handlerMapping.unregisterMapping(combinedMappingInfo);

                    // Register the mapping with our fully initialized proxy bean instance.
                    handlerMapping.registerMapping(combinedMappingInfo, beanInstance, method);

                    // Log the registered mapping for debugging purposes
                    log.info("Registered proxy endpoint: {} {} -> {}.{}",
                            combinedMappingInfo.getMethodsCondition(),
                            combinedMappingInfo.getPatternsCondition(),
                            userFacingInterface.getSimpleName(),
                            method.getName());
                }
            }
        });
    }

    @Bean
    public static ProxyControllerRegistrar proxyControllerRegistrar() {
        return new ProxyControllerRegistrar();
    }

    static class ProxyControllerRegistrar implements BeanDefinitionRegistryPostProcessor {
        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            List<String> basePackages = AutoConfigurationPackages.get((BeanFactory) registry);
            if (basePackages == null || basePackages.isEmpty())
                return;

            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                    false) {
                @Override
                protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                    return beanDefinition.getMetadata().isInterface();
                }
            };
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            scanner.addIncludeFilter(new AnnotationTypeFilter(ServiceClass.class));

            for (String basePackage : basePackages) {
                Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
                for (BeanDefinition candidate : candidates) {
                    try {
                        String interfaceName = candidate.getBeanClassName();
                        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                                .genericBeanDefinition(ControllerProxyFactoryBean.class);
                        builder.addConstructorArgValue(Class.forName(interfaceName));
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
            // No-op
        }
    }
}