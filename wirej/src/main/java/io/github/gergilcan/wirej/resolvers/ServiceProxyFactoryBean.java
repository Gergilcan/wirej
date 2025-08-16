package io.github.gergilcan.wirej.resolvers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;

public class ServiceProxyFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private final Class<T> proxyInterface;
    private ApplicationContext applicationContext;

    public ServiceProxyFactoryBean(Class<T> proxyInterface) {
        Assert.notNull(proxyInterface, "Proxy interface must not be null");
        this.proxyInterface = proxyInterface;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public T getObject() throws Exception {
        ServiceClass serviceClassAnnotation = proxyInterface.getAnnotation(ServiceClass.class);
        if (serviceClassAnnotation == null) {
            throw new IllegalStateException(
                    "The interface " + proxyInterface.getName() + " must be annotated with @ServiceClass");
        }
        Class<?> targetServiceClass = serviceClassAnnotation.value();

        Object targetServiceBean = applicationContext.getBean(targetServiceClass);

        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            ServiceMethod serviceMethodAnnotation = method.getAnnotation(ServiceMethod.class);
            if (serviceMethodAnnotation == null) {
                throw new UnsupportedOperationException("Method " + method.getName() + " in " + proxyInterface.getName()
                        + " is not annotated with @ServiceMethod");
            }

            String targetMethodName = serviceMethodAnnotation.value();
            if (!StringUtils.hasText(targetMethodName)) {
                targetMethodName = method.getName();
            }

            Method targetMethod = targetServiceClass.getMethod(targetMethodName, method.getParameterTypes());
            // Invoke the actual service method
            Object resultFromService = targetMethod.invoke(targetServiceBean, args);

            // If the service already returned a ResponseEntity, pass it through.
            // This allows for advanced cases where the service needs to control headers,
            // etc.
            if (resultFromService instanceof ResponseEntity) {
                return resultFromService;
            }

            // Otherwise, wrap the result from the service into a ResponseEntity.
            // Get the desired HTTP status from the @ResponseStatus annotation on the
            // interface method.
            ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

            // Default to HttpStatus.OK if the annotation is not present.
            HttpStatus status = (responseStatus != null) ? responseStatus.value() : HttpStatus.OK;

            // Create the ResponseEntity with the service result as the body and the
            // determined status.
            return new ResponseEntity<>(resultFromService, status);
        };

        @SuppressWarnings("unchecked")
        T proxyInstance = (T) Proxy.newProxyInstance(
                proxyInterface.getClassLoader(),
                new Class<?>[] { proxyInterface },
                handler);
        return proxyInstance;
    }

    @Override
    public Class<?> getObjectType() {
        return this.proxyInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
