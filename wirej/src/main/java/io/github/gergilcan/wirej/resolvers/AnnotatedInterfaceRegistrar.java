package io.github.gergilcan.wirej.resolvers;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class AnnotatedInterfaceRegistrar {

    protected final void scanAndRegister(BeanDefinitionRegistry registry, Class<? extends Annotation> marker) {
        List<String> basePackages = AutoConfigurationPackages.get((BeanFactory) registry);
        if (basePackages == null || basePackages.isEmpty()) {
            log.warn("Could not determine base packages for {} scanning.", marker.getSimpleName());
            return;
        }

        log.info("Scanning for @{} interfaces in packages: {}", marker.getSimpleName(), basePackages);

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(marker));

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                registerCandidate(registry, candidate);
            }
        }
    }

    private void registerCandidate(BeanDefinitionRegistry registry, BeanDefinition candidate) {
        try {
            Class<?> candidateInterface = Class.forName(candidate.getBeanClassName());
            if (!accept(candidateInterface)) {
                return;
            }

            String beanName = decapitalize(candidateInterface.getSimpleName());
            registerBean(registry, beanName, candidateInterface);
            log.debug("Registered proxy: {} for interface: {}", beanName, candidateInterface.getName());
        } catch (ClassNotFoundException e) {
            log.error("Could not load class for proxy: {}", candidate.getBeanClassName(), e);
        }
    }

    protected boolean accept(Class<?> candidateInterface) {
        return true;
    }

    protected abstract void registerBean(BeanDefinitionRegistry registry, String beanName, Class<?> candidateInterface);

    private static String decapitalize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
