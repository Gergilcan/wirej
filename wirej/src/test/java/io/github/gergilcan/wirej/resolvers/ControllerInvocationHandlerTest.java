package io.github.gergilcan.wirej.resolvers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;

class ControllerInvocationHandlerTest {

    public static class GreetingService {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    @ServiceClass(GreetingService.class)
    public interface Greeter {
        @ServiceMethod
        ResponseEntity<?> greet(String name);
    }

    private Greeter newProxy(ControllerInvocationHandler handler) {
        return (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(), new Class<?>[] { Greeter.class }, handler);
    }

    @Test
    void resolvedServiceMethodStillInvokesCorrectly() {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        GreetingService service = new GreetingService();
        Mockito.when(applicationContext.getBean(GreetingService.class)).thenReturn(service);

        Greeter proxy = newProxy(new ControllerInvocationHandler(applicationContext, GreetingService.class));

        assertThat(proxy.greet("Ada").getBody()).isEqualTo("Hello, Ada");
        assertThat(proxy.greet("Grace").getBody()).isEqualTo("Hello, Grace");
    }

    @Test
    void serviceMethodResolutionIsCachedAcrossInvocations() throws Exception {
        ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
        GreetingService service = new GreetingService();
        Mockito.when(applicationContext.getBean(GreetingService.class)).thenReturn(service);

        ControllerInvocationHandler handler = new ControllerInvocationHandler(applicationContext,
                GreetingService.class);
        Greeter proxy = newProxy(handler);

        Field cacheField = ControllerInvocationHandler.class.getDeclaredField("serviceMethodCache");
        cacheField.setAccessible(true);

        proxy.greet("Ada");
        @SuppressWarnings("unchecked")
        Map<Method, Method> cacheAfterFirstCall = (Map<Method, Method>) cacheField.get(handler);
        assertThat(cacheAfterFirstCall).hasSize(1);
        Method cachedAfterFirstCall = cacheAfterFirstCall.values().iterator().next();

        proxy.greet("Grace");
        @SuppressWarnings("unchecked")
        Map<Method, Method> cacheAfterSecondCall = (Map<Method, Method>) cacheField.get(handler);
        assertThat(cacheAfterSecondCall).hasSize(1);
        assertThat(cacheAfterSecondCall.values().iterator().next()).isSameAs(cachedAfterFirstCall);
    }
}
