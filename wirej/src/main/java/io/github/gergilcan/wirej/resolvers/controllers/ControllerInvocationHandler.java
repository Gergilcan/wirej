package io.github.gergilcan.wirej.resolvers.controllers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.github.gergilcan.wirej.annotations.ServiceMethod;
import io.github.gergilcan.wirej.annotations.ValidatePermission;
import io.github.gergilcan.wirej.core.security.PermissionValidator;

public class ControllerInvocationHandler implements InvocationHandler {
    private final ApplicationContext applicationContext;
    private final Class<?> serviceClass;
    private final PermissionValidator permissionValidator;

    public ControllerInvocationHandler(ApplicationContext applicationContext, Class<?> serviceClass,
            PermissionValidator permissionValidator) {
        this.applicationContext = applicationContext;
        this.serviceClass = serviceClass;
        this.permissionValidator = permissionValidator;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Get the service method annotation
        ServiceMethod serviceMethodAnnotation = method.getAnnotation(ServiceMethod.class);
        if (serviceMethodAnnotation == null) {
            throw new RuntimeException("Method " + method.getName() + " is not annotated with @ServiceMethod");
        }

        // Determine the service method name to call
        String serviceMethodName = serviceMethodAnnotation.value().isEmpty()
                ? method.getName()
                : serviceMethodAnnotation.value();

        // Get the service bean from Spring context
        Object serviceBean = applicationContext.getBean(serviceClass);

        // Find the method in the service class
        Method serviceMethod = findServiceMethod(serviceClass, serviceMethodName, method.getParameterTypes());
        if (serviceMethod == null) {
            throw new RuntimeException(
                    "Service method " + serviceMethodName + " not found in " + serviceClass.getName());
        }

        // Invoke the validation
        ValidatePermission validatePermission = method.getAnnotation(ValidatePermission.class);
        if (validatePermission != null) {
            permissionValidator.initialize(validatePermission);
            permissionValidator.isValid(method, null); // The second parameter is not used in this context
        }

        // Invoke the service method and get the result
        Object serviceResult = serviceMethod.invoke(serviceBean, args);

        // Get the HTTP status from @ResponseStatus annotation
        ResponseStatus responseStatusAnnotation = method.getAnnotation(ResponseStatus.class);
        HttpStatus status = responseStatusAnnotation != null ? responseStatusAnnotation.value() : HttpStatus.OK;

        // Wrap the result in a ResponseEntity
        if (serviceMethod.getReturnType() == Void.TYPE) {
            // If service method returns void, return ResponseEntity without body
            return ResponseEntity.status(status).build();
        } else {
            // If service method returns a value, use it as the body
            return ResponseEntity.status(status).body(serviceResult);
        }
    }

    private Method findServiceMethod(Class<?> serviceClass, String methodName, Class<?>[] parameterTypes) {
        try {
            // First try exact parameter type matching
            return serviceClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            // If exact match fails, try to find by name and parameter count
            return findCompatibleMethod(serviceClass, methodName, parameterTypes);
        }
    }

    private Method findCompatibleMethod(Class<?> serviceClass, String methodName, Class<?>[] parameterTypes) {
        Method[] methods = serviceClass.getDeclaredMethods();
        for (Method method : methods) {
            if (isCompatibleMethod(method, methodName, parameterTypes)) {
                return method;
            }
        }
        return null;
    }

    private boolean isCompatibleMethod(Method method, String methodName, Class<?>[] parameterTypes) {
        if (!method.getName().equals(methodName) || method.getParameterCount() != parameterTypes.length) {
            return false;
        }

        Class<?>[] serviceMethodParams = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!serviceMethodParams[i].isAssignableFrom(parameterTypes[i])) {
                return false;
            }
        }
        return true;
    }
}