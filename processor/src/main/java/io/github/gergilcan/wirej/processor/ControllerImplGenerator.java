package io.github.gergilcan.wirej.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
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
import com.squareup.javapoet.ParameterizedTypeName;
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

    /**
     * {@code batchServiceMethod} is null for an ordinary @ServiceMethod. When
     * present, {@code controllerMethod} accepts a single JsonNode body that is
     * sniffed at runtime for single-vs-array shape: {@code serviceMethod} is
     * the single-item overload to call, {@code batchServiceMethod} the
     * array/list overload.
     */
    record ResolvedMethod(ExecutableElement controllerMethod, ExecutableType controllerMethodType,
            ExecutableElement serviceMethod, ExecutableElement batchServiceMethod) {
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

        boolean needsObjectMapper = methods.stream().anyMatch(m -> m.batchServiceMethod() != null);

        typeBuilder.addField(serviceTypeName, "service", Modifier.PRIVATE, Modifier.FINAL);
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(serviceTypeName, "service")
                .addStatement("this.service = service");
        if (needsObjectMapper) {
            typeBuilder.addField(WireJTypes.OBJECT_MAPPER, "objectMapper", Modifier.PRIVATE, Modifier.FINAL);
            constructor.addParameter(WireJTypes.OBJECT_MAPPER, "objectMapper");
            constructor.addStatement("this.objectMapper = objectMapper");
        }
        typeBuilder.addMethod(constructor.build());

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
        if (resolved.batchServiceMethod() != null) {
            return buildBatchMethod(resolved);
        }

        ExecutableElement controllerMethod = resolved.controllerMethod();
        ExecutableType controllerMethodType = resolved.controllerMethodType();
        ExecutableElement serviceMethod = resolved.serviceMethod();

        MethodSpec.Builder method = MethodSpec.methodBuilder(controllerMethod.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(controllerMethodType.getReturnType()));

        for (AnnotationMirror mirror : controllerMethod.getAnnotationMirrors()) {
            if (isAnnotation(mirror, SERVICE_METHOD)) {
                continue;
            }
            method.addAnnotation(AnnotationSpec.get(mirror));
        }

        // Parameter NAMES/ANNOTATIONS come from the declared element, but the TYPE must come from
        // controllerMethodType - for a method inherited from a generic interface (e.g.
        // StandardRestRepository<T, ID>), parameter.asType() would still show the raw type variable
        // (ID), while controllerMethodType has it substituted to the concrete type (Long).
        List<? extends VariableElement> parameters = controllerMethod.getParameters();
        List<? extends TypeMirror> parameterTypes = controllerMethodType.getParameterTypes();
        List<String> argNames = parameters.stream().map(p -> p.getSimpleName().toString()).toList();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement parameter = parameters.get(i);
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(TypeName.get(parameterTypes.get(i)),
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

    /**
     * Builds a {@code @ServiceMethod(batchSupported = true)} dispatch: the
     * JsonNode body is sniffed for {@code isArray()} at runtime, and each
     * branch calls a different resolved service method. The batch service
     * method's own parameter shape tells us which pair this is - an array
     * parameter means "create" (entity / entity[]), a
     * {@code List<BatchPatchItem<ID>>} parameter means "patch" (id+changes /
     * batch items), since those are the only two shapes {@code
     * ServiceMethodProcessor} ever resolves this way.
     */
    private MethodSpec buildBatchMethod(ResolvedMethod resolved) {
        ExecutableElement controllerMethod = resolved.controllerMethod();
        ExecutableType controllerMethodType = resolved.controllerMethodType();
        ExecutableElement singleServiceMethod = resolved.serviceMethod();
        ExecutableElement batchServiceMethod = resolved.batchServiceMethod();

        MethodSpec.Builder method = MethodSpec.methodBuilder(controllerMethod.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build())
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(controllerMethodType.getReturnType()));

        for (AnnotationMirror mirror : controllerMethod.getAnnotationMirrors()) {
            if (isAnnotation(mirror, SERVICE_METHOD)) {
                continue;
            }
            method.addAnnotation(AnnotationSpec.get(mirror));
        }

        List<? extends VariableElement> parameters = controllerMethod.getParameters();
        List<? extends TypeMirror> parameterTypes = controllerMethodType.getParameterTypes();
        String bodyParamName = parameters.get(0).getSimpleName().toString();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement parameter = parameters.get(i);
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(TypeName.get(parameterTypes.get(i)),
                    parameter.getSimpleName().toString());
            for (AnnotationMirror mirror : parameter.getAnnotationMirrors()) {
                parameterBuilder.addAnnotation(AnnotationSpec.get(mirror));
            }
            method.addParameter(parameterBuilder.build());
        }

        CodeBlock statusExpr = resolveResponseStatus(controllerMethod);
        TypeMirror batchParamType = batchServiceMethod.getParameters().get(0).asType();
        boolean isCreateShape = batchParamType.getKind() == TypeKind.ARRAY;

        CodeBlock.Builder core = CodeBlock.builder();
        if (isCreateShape) {
            addCreateShapeDispatch(core, bodyParamName, batchParamType, singleServiceMethod, batchServiceMethod,
                    statusExpr);
        } else {
            addPatchShapeDispatch(core, bodyParamName, batchParamType, singleServiceMethod, batchServiceMethod,
                    statusExpr);
        }

        List<TypeMirror> checkedExceptions = mergedCheckedExceptions(singleServiceMethod, batchServiceMethod);
        boolean needsTry = isCreateShape || !checkedExceptions.isEmpty();

        CodeBlock.Builder body = CodeBlock.builder();
        if (needsTry) {
            body.beginControlFlow("try");
        }
        body.add(core.build());
        if (isCreateShape) {
            body.nextControlFlow("catch ($T e)", WireJTypes.JSON_PROCESSING_EXCEPTION);
            body.addStatement("throw new $T($S + e.getMessage(), e)", WireJTypes.WIREJ_EXCEPTION,
                    "Failed to parse request body for '" + controllerMethod.getSimpleName() + "': ");
        }
        if (!checkedExceptions.isEmpty()) {
            String catchHeader = "catch (" + checkedExceptions.stream().map(t -> "$T")
                    .collect(Collectors.joining(" | ")) + " e)";
            body.nextControlFlow(catchHeader, checkedExceptions.stream().map(TypeName::get).toArray());
            body.addStatement("throw new $T($S + e.getMessage(), e)", WireJTypes.WIREJ_EXCEPTION,
                    "Service call to '" + singleServiceMethod.getSimpleName() + "' failed: ");
        }
        if (needsTry) {
            body.endControlFlow();
        }

        method.addCode(body.build());
        return method.build();
    }

    private void addCreateShapeDispatch(CodeBlock.Builder body, String bodyParamName, TypeMirror batchParamType,
            ExecutableElement singleServiceMethod, ExecutableElement batchServiceMethod, CodeBlock statusExpr) {
        TypeName entityTypeName = TypeName.get(((ArrayType) batchParamType).getComponentType());

        body.beginControlFlow("if ($L.isArray())", bodyParamName);
        body.addStatement("$T<$T> items = new $T<>()", List.class, entityTypeName, ArrayList.class);
        body.beginControlFlow("for ($T element : $L)", WireJTypes.JSON_NODE, bodyParamName);
        body.addStatement("items.add(objectMapper.treeToValue(element, $T.class))", entityTypeName);
        body.endControlFlow();
        body.addStatement("$T[] result = this.service.$L(items.toArray(new $T[0]))", entityTypeName,
                batchServiceMethod.getSimpleName(), entityTypeName);
        body.addStatement("return $T.status($L).body(result)", WireJTypes.RESPONSE_ENTITY, statusExpr);
        body.nextControlFlow("else");
        body.addStatement("$T entity = objectMapper.treeToValue($L, $T.class)", entityTypeName, bodyParamName,
                entityTypeName);
        body.addStatement("$T result = this.service.$L(entity)", entityTypeName,
                singleServiceMethod.getSimpleName());
        body.addStatement("return $T.status($L).body(result)", WireJTypes.RESPONSE_ENTITY, statusExpr);
        body.endControlFlow();
    }

    private void addPatchShapeDispatch(CodeBlock.Builder body, String bodyParamName, TypeMirror batchParamType,
            ExecutableElement singleServiceMethod, ExecutableElement batchServiceMethod, CodeBlock statusExpr) {
        DeclaredType listType = (DeclaredType) batchParamType;
        DeclaredType batchPatchItemType = (DeclaredType) listType.getTypeArguments().get(0);
        TypeMirror idType = batchPatchItemType.getTypeArguments().get(0);
        TypeName idTypeName = TypeName.get(idType).box();
        TypeName batchPatchItemTypeName = TypeName.get(batchPatchItemType);
        TypeName mapType = ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(String.class),
                ClassName.get(Object.class));

        body.beginControlFlow("if ($L.isArray())", bodyParamName);
        body.addStatement("$T<$T> items = new $T<>()", List.class, batchPatchItemTypeName, ArrayList.class);
        body.beginControlFlow("for ($T element : $L)", WireJTypes.JSON_NODE, bodyParamName);
        body.addStatement("$T changes = objectMapper.convertValue(element, $T.class)", mapType, Map.class);
        body.addStatement("$T id = objectMapper.convertValue(changes.remove($S), $T.class)", idTypeName, "id",
                idTypeName);
        body.addStatement("items.add(new $T<>(id, changes))", WireJTypes.BATCH_PATCH_ITEM);
        body.endControlFlow();
        body.addStatement("$T result = this.service.$L(items)", TypeName.get(batchServiceMethod.getReturnType()),
                batchServiceMethod.getSimpleName());
        body.addStatement("return $T.status($L).body(result)", WireJTypes.RESPONSE_ENTITY, statusExpr);
        body.nextControlFlow("else");
        body.addStatement("$T changes = objectMapper.convertValue($L, $T.class)", mapType, bodyParamName, Map.class);
        body.addStatement("$T id = objectMapper.convertValue(changes.remove($S), $T.class)", idTypeName, "id",
                idTypeName);
        body.addStatement("$T result = this.service.$L(id, changes)", TypeName.get(singleServiceMethod
                .getReturnType()), singleServiceMethod.getSimpleName());
        body.addStatement("return $T.status($L).body(result)", WireJTypes.RESPONSE_ENTITY, statusExpr);
        body.endControlFlow();
    }

    private List<TypeMirror> mergedCheckedExceptions(ExecutableElement a, ExecutableElement b) {
        Map<String, TypeMirror> merged = new LinkedHashMap<>();
        for (TypeMirror thrown : a.getThrownTypes()) {
            if (isCheckedException(thrown)) {
                merged.putIfAbsent(thrown.toString(), thrown);
            }
        }
        for (TypeMirror thrown : b.getThrownTypes()) {
            if (isCheckedException(thrown)) {
                merged.putIfAbsent(thrown.toString(), thrown);
            }
        }
        return new ArrayList<>(merged.values());
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
