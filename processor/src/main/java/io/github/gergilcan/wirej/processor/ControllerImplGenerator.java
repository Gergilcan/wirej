package io.github.gergilcan.wirej.processor;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;

/**
 * Generates a real implementing class for each interface annotated
 * {@code @RestController} + {@code @ServiceClass}, replacing the runtime
 * {@code ControllerInvocationHandler} dynamic proxy with a plain, debuggable
 * class the compiler checks and a debugger can step into.
 */
final class ControllerImplGenerator {
    private static final String SERVICE_METHOD = ServiceMethod.class.getCanonicalName();
    private static final String SERVICE_CLASS = ServiceClass.class.getCanonicalName();
    private static final String RESPONSE_STATUS = "org.springframework.web.bind.annotation.ResponseStatus";

    record ResolvedMethod(ExecutableElement controllerMethod, ExecutableElement serviceMethod) {
    }

    private final Filer filer;
    private final Messager messager;
    private final Elements elements;

    ControllerImplGenerator(Filer filer, Messager messager, Elements elements) {
        this.filer = filer;
        this.messager = messager;
        this.elements = elements;
    }

    void generate(TypeElement controllerInterface, TypeMirror serviceClassMirror, List<ResolvedMethod> methods) {
        ClassName interfaceName = ClassName.get(controllerInterface);
        String implName = interfaceName.simpleName() + "Impl";
        TypeName serviceTypeName = TypeName.get(serviceClassMirror);

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(TypeName.get(controllerInterface.asType()));

        for (AnnotationMirror mirror : controllerInterface.getAnnotationMirrors()) {
            if (isAnnotation(mirror, SERVICE_CLASS)) {
                continue;
            }
            typeBuilder.addAnnotation(AnnotationSpec.get(mirror));
        }

        typeBuilder.addField(serviceTypeName, "service", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(serviceTypeName, "service")
                .addStatement("this.service = service")
                .build());

        for (ResolvedMethod resolved : methods) {
            typeBuilder.addMethod(buildMethod(resolved));
        }

        try {
            JavaFile.builder(interfaceName.packageName(), typeBuilder.build()).build().writeTo(filer);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write generated controller implementation: " + e.getMessage(), controllerInterface);
        }
    }

    private MethodSpec buildMethod(ResolvedMethod resolved) {
        ExecutableElement controllerMethod = resolved.controllerMethod();
        ExecutableElement serviceMethod = resolved.serviceMethod();

        MethodSpec.Builder method = MethodSpec.methodBuilder(controllerMethod.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(controllerMethod.getReturnType()));

        for (AnnotationMirror mirror : controllerMethod.getAnnotationMirrors()) {
            if (isAnnotation(mirror, SERVICE_METHOD)) {
                continue;
            }
            method.addAnnotation(AnnotationSpec.get(mirror));
        }

        List<String> argNames = controllerMethod.getParameters().stream()
                .map(p -> p.getSimpleName().toString()).toList();
        for (VariableElement parameter : controllerMethod.getParameters()) {
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(TypeName.get(parameter.asType()),
                    parameter.getSimpleName().toString());
            for (AnnotationMirror mirror : parameter.getAnnotationMirrors()) {
                parameterBuilder.addAnnotation(AnnotationSpec.get(mirror));
            }
            method.addParameter(parameterBuilder.build());
        }

        CodeBlock statusExpr = resolveResponseStatus(controllerMethod);
        String callArgs = String.join(", ", argNames);
        boolean serviceReturnsVoid = serviceMethod.getReturnType().getKind() == TypeKind.VOID;
        List<? extends TypeMirror> checkedExceptions = serviceMethod.getThrownTypes().stream()
                .filter(this::isCheckedException).toList();

        CodeBlock.Builder body = CodeBlock.builder();
        if (!checkedExceptions.isEmpty()) {
            body.beginControlFlow("try");
        }

        if (serviceReturnsVoid) {
            body.addStatement("this.service.$L($L)", serviceMethod.getSimpleName(), callArgs);
            body.addStatement("return $T.status($L).build()", WireJTypes.RESPONSE_ENTITY, statusExpr);
        } else {
            body.addStatement("$T result = this.service.$L($L)", TypeName.get(serviceMethod.getReturnType()),
                    serviceMethod.getSimpleName(), callArgs);
            body.addStatement("return $T.status($L).body(result)", WireJTypes.RESPONSE_ENTITY, statusExpr);
        }

        if (!checkedExceptions.isEmpty()) {
            String catchHeader = "catch (" + checkedExceptions.stream().map(t -> "$T")
                    .collect(Collectors.joining(" | ")) + " e)";
            body.nextControlFlow(catchHeader, checkedExceptions.stream().map(TypeName::get).toArray());
            body.addStatement("throw new $T($S + e.getMessage(), e)", WireJTypes.WIREJ_EXCEPTION,
                    "Service call to '" + serviceMethod.getSimpleName() + "' failed: ");
            body.endControlFlow();
        }

        method.addCode(body.build());
        return method.build();
    }

    private boolean isCheckedException(TypeMirror thrown) {
        return !ProcessorSupport.isType(thrown, "java.lang.RuntimeException")
                && !ProcessorSupport.isType(thrown, "java.lang.Error");
    }

    private CodeBlock resolveResponseStatus(ExecutableElement controllerMethod) {
        for (AnnotationMirror mirror : controllerMethod.getAnnotationMirrors()) {
            if (!isAnnotation(mirror, RESPONSE_STATUS)) {
                continue;
            }
            for (var entry : elements.getElementValuesWithDefaults(mirror).entrySet()) {
                String memberName = entry.getKey().getSimpleName().toString();
                if (!memberName.equals("value") && !memberName.equals("code")) {
                    continue;
                }
                AnnotationValue value = entry.getValue();
                if (value.getValue() instanceof VariableElement enumConstant) {
                    return CodeBlock.of("$T.$L", TypeName.get(enumConstant.asType()), enumConstant.getSimpleName());
                }
            }
        }
        return CodeBlock.of("$T.OK", WireJTypes.HTTP_STATUS);
    }

    private boolean isAnnotation(AnnotationMirror mirror, String qualifiedName) {
        TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
        return annotationType.getQualifiedName().contentEquals(qualifiedName);
    }
}
