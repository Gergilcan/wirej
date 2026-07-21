package io.github.gergilcan.wirej.processor;

import com.google.auto.service.AutoService;
import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Explicitly list all annotations the processor might interact with.
@SupportedAnnotationTypes({
        "io.github.gergilcan.wirej.annotations.ServiceMethod",
        "io.github.gergilcan.wirej.annotations.ServiceClass",
        "io.github.gergilcan.wirej.annotations.QueryFile"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class ServiceMethodProcessor extends AbstractProcessor {
    private Messager messager;
    private ControllerImplGenerator controllerImplGenerator;
    private RepositoryImplGenerator repositoryImplGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        controllerImplGenerator = new ControllerImplGenerator(processingEnv.getFiler(), messager,
                processingEnv.getElementUtils());
        repositoryImplGenerator = new RepositoryImplGenerator(processingEnv.getFiler(), messager,
                processingEnv.getElementUtils());
        messager.printMessage(Diagnostic.Kind.NOTE, "ServiceMethodProcessor initialized.");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        messager.printMessage(Diagnostic.Kind.NOTE, "Starting new processing round.");

        processServiceMethodAnnotations(roundEnv);
        processQueryFileAnnotations(roundEnv);

        messager.printMessage(Diagnostic.Kind.NOTE, "Finished processing round.");
        return false; // Return false to allow other processors to see the annotations
    }

    private void processServiceMethodAnnotations(RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(ServiceMethod.class);

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Found " + annotatedElements.size() + " elements annotated with @ServiceMethod in this round.");

        Map<TypeElement, List<ExecutableElement>> methodsByInterface = annotatedElements.stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .collect(Collectors.groupingBy(method -> (TypeElement) method.getEnclosingElement()));

        for (var entry : methodsByInterface.entrySet()) {
            TypeElement controllerInterface = entry.getKey();
            List<ExecutableElement> controllerMethods = entry.getValue();

            ServiceClass serviceClassAnnotation = controllerInterface.getAnnotation(ServiceClass.class);
            if (serviceClassAnnotation == null) {
                error(controllerInterface,
                        "The class/interface containing @ServiceMethod must be annotated with @ServiceClass.");
                continue;
            }

            TypeMirror targetServiceClassMirror = getServiceClassValue(serviceClassAnnotation);
            if (targetServiceClassMirror == null) {
                error(controllerInterface, "Could not resolve target service class for @ServiceClass annotation.");
                continue;
            }

            List<ControllerImplGenerator.ResolvedMethod> resolvedMethods = new ArrayList<>();
            boolean allResolved = true;

            for (ExecutableElement annotatedMethod : controllerMethods) {
                ServiceMethod serviceMethodAnnotation = annotatedMethod.getAnnotation(ServiceMethod.class);
                String targetMethodName = serviceMethodAnnotation.value();

                if (targetMethodName == null || targetMethodName.trim().isEmpty()) {
                    targetMethodName = annotatedMethod.getSimpleName().toString();
                    messager.printMessage(Diagnostic.Kind.NOTE,
                            "Using method name '" + targetMethodName + "' for @ServiceMethod on " +
                                    annotatedMethod.getSimpleName());
                }

                Optional<ExecutableElement> matchingMethod = findMatchingMethod(annotatedMethod,
                        targetServiceClassMirror, targetMethodName);

                if (matchingMethod.isEmpty()) {
                    String paramsString = annotatedMethod.getParameters().stream()
                            .map(p -> p.asType().toString())
                            .collect(Collectors.joining(", "));
                    error(annotatedMethod, "Method '%s(%s)' does not exist in class %s", targetMethodName,
                            paramsString, targetServiceClassMirror.toString());
                    allResolved = false;
                    continue;
                }

                checkReturnTypeCompatible(annotatedMethod, matchingMethod.get(), targetMethodName,
                        targetServiceClassMirror);
                resolvedMethods.add(new ControllerImplGenerator.ResolvedMethod(annotatedMethod, matchingMethod.get()));
            }

            if (allResolved) {
                controllerImplGenerator.generate(controllerInterface, targetServiceClassMirror, resolvedMethods);
            }
        }
    }

    private void processQueryFileAnnotations(RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(QueryFile.class);

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Found " + annotatedElements.size() + " elements annotated with @QueryFile in this round.");

        Map<TypeElement, List<ExecutableElement>> methodsByInterface = annotatedElements.stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .collect(Collectors.groupingBy(method -> (TypeElement) method.getEnclosingElement()));

        for (var entry : methodsByInterface.entrySet()) {
            TypeElement repositoryInterface = entry.getKey();
            List<ExecutableElement> methods = entry.getValue();
            boolean allValid = true;

            for (ExecutableElement annotatedMethod : methods) {
                QueryFile queryFileAnnotation = annotatedMethod.getAnnotation(QueryFile.class);
                String queryFilePath = queryFileAnnotation.value();
                if (queryFilePath == null || queryFilePath.trim().isEmpty()) {
                    error(annotatedMethod, "@QueryFile annotation must specify a file path");
                    allValid = false;
                    continue;
                }

                String resourcePath = queryFilePath.startsWith("/") ? queryFilePath.substring(1) : queryFilePath;
                if (!validateQueryFileExists(annotatedMethod, resourcePath)) {
                    allValid = false;
                }
            }

            if (allValid) {
                repositoryImplGenerator.generate(repositoryInterface, methods);
            }
        }
    }

    private static final List<StandardLocation> RESOURCE_LOOKUP_LOCATIONS = List.of(
            StandardLocation.ANNOTATION_PROCESSOR_PATH, StandardLocation.CLASS_PATH, StandardLocation.CLASS_OUTPUT);

    private boolean validateQueryFileExists(ExecutableElement method, String resourcePath) {
        for (StandardLocation location : RESOURCE_LOOKUP_LOCATIONS) {
            try (var in = processingEnv.getFiler().getResource(location, "", resourcePath).openInputStream()) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                        "✓ Found query file: " + resourcePath + " in " + location + " for method "
                                + method.getSimpleName());
                return true;
            } catch (IOException e) {
            }
        }

        error(method, "Query file not found during compilation: " + resourcePath +
                " for method " + method.getSimpleName() +
                ". This might be normal if the resource hasn't been processed yet. " +
                "Ensure the file exists in src/main/resources/" + resourcePath +
                " or src/test/resources/" + resourcePath);
        return false;
    }

    private Optional<ExecutableElement> findMatchingMethod(ExecutableElement sourceMethod, TypeMirror targetClass,
            String methodName) {
        TypeElement targetClassElement = (TypeElement) processingEnv.getTypeUtils().asElement(targetClass);
        Types typeUtils = processingEnv.getTypeUtils();

        List<? extends TypeMirror> sourceParamTypes = sourceMethod.getParameters().stream()
                .map(VariableElement::asType).toList();

        return targetClassElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(targetMethod -> isCompatibleMethod(typeUtils, sourceParamTypes, targetMethod, methodName))
                .findFirst();
    }

    private boolean isCompatibleMethod(Types typeUtils, List<? extends TypeMirror> sourceParamTypes,
            ExecutableElement targetMethod, String methodName) {
        if (!targetMethod.getSimpleName().toString().equals(methodName)) {
            return false;
        }

        List<? extends VariableElement> targetParams = targetMethod.getParameters();
        if (sourceParamTypes.size() != targetParams.size()) {
            return false;
        }

        for (int i = 0; i < sourceParamTypes.size(); i++) {
            if (!typeUtils.isAssignable(sourceParamTypes.get(i), targetParams.get(i).asType())) {
                return false;
            }
        }
        return true;
    }

    private void checkReturnTypeCompatible(ExecutableElement sourceMethod, ExecutableElement targetMethod,
            String methodName, TypeMirror targetClass) {
        TypeMirror sourceReturnType = sourceMethod.getReturnType();
        if (sourceReturnType.getKind() != TypeKind.DECLARED) {
            return;
        }

        List<? extends TypeMirror> typeArguments = ((DeclaredType) sourceReturnType).getTypeArguments();
        if (typeArguments.isEmpty() || typeArguments.get(0).getKind() == TypeKind.WILDCARD) {
            return;
        }

        TypeMirror expectedBodyType = typeArguments.get(0);
        TypeMirror actualReturnType = targetMethod.getReturnType();
        if (actualReturnType.getKind() == TypeKind.VOID) {
            return;
        }

        if (!processingEnv.getTypeUtils().isAssignable(actualReturnType, expectedBodyType)) {
            // The generated controller impl calls ResponseEntity.status(...).body(result) with the service
            // method's actual return type, so a mismatch here is always a hard compile failure downstream.
            // Reporting it as an error here, against the user's own interface method, gives a much clearer
            // diagnostic than the generic-inference error javac would otherwise raise inside generated code.
            error(sourceMethod,
                    "Method '%s' in class %s returns %s, which is not assignable to the declared response body type %s",
                    methodName, targetClass.toString(), actualReturnType.toString(), expectedBodyType.toString());
        }
    }

    private TypeMirror getServiceClassValue(ServiceClass annotation) {
        try {
            annotation.value();
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror();
        }
        return null;
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }
}
