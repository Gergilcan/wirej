package io.github.gergilcan.wirej.resolvers;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;

import io.github.gergilcan.wirej.annotations.ServiceClass;

@Configuration(proxyBeanMethods = false)
public class ProxyControllerAutoConfiguration extends AnnotatedInterfaceRegistrar
        implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        scanAndRegister(registry, RestController.class);
    }

    @Override
    protected boolean accept(Class<?> candidateInterface) {
        return candidateInterface.isAnnotationPresent(ServiceClass.class);
    }

    @Override
    protected void registerBean(BeanDefinitionRegistry registry, String beanName, Class<?> controllerInterface) {
        Class<?> serviceClass = controllerInterface.getAnnotation(ServiceClass.class).value();

        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(ControllerProxyFactoryBean.class);
        builder.addConstructorArgValue(controllerInterface);
        builder.addConstructorArgValue(serviceClass);

        // Ensure the bean definition has the correct type
        BeanDefinition beanDefinition = builder.getBeanDefinition();
        beanDefinition.setAttribute("factoryBeanObjectType", controllerInterface);

        registry.registerBeanDefinition(beanName, beanDefinition);
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