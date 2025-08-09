package io.github.gergilcan.wirej.processor;

import com.google.auto.service.AutoService;
import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.List;
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

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE, "ServiceMethodProcessor is running.");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        messager.printMessage(Diagnostic.Kind.NOTE, "Starting new processing round.");

        // Process @ServiceMethod annotations
        processServiceMethodAnnotations(roundEnv);

        // Process @QueryFile annotations
        processQueryFileAnnotations(roundEnv);

        messager.printMessage(Diagnostic.Kind.NOTE, "Finished processing round.");
        return false; // Return false to allow other processors to see the annotations
    }

    private void processServiceMethodAnnotations(RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(ServiceMethod.class);

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Found " + annotatedElements.size() + " elements annotated with @ServiceMethod in this round.");

        for (Element annotatedElement : annotatedElements) {
            if (annotatedElement.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement annotatedMethod = (ExecutableElement) annotatedElement;
            TypeElement enclosingClass = (TypeElement) annotatedMethod.getEnclosingElement();
            ServiceClass serviceClassAnnotation = enclosingClass.getAnnotation(ServiceClass.class);
            ServiceMethod serviceMethodAnnotation = annotatedMethod.getAnnotation(ServiceMethod.class);

            if (serviceClassAnnotation == null) {
                error(enclosingClass,
                        "The class/interface containing @ServiceMethod must be annotated with @ServiceClass.");
                continue;
            }

            TypeMirror targetServiceClassMirror = getServiceClassValue(serviceClassAnnotation);
            String targetMethodName = serviceMethodAnnotation.value();

            // If @ServiceMethod value is empty, use the annotated method name
            if (targetMethodName == null || targetMethodName.trim().isEmpty()) {
                targetMethodName = annotatedMethod.getSimpleName().toString();
                messager.printMessage(Diagnostic.Kind.NOTE,
                        "Using method name '" + targetMethodName + "' for @ServiceMethod on " +
                                annotatedMethod.getSimpleName());
            }

            if (targetServiceClassMirror == null) {
                error(enclosingClass,
                        "Could not resolve target service class for @ServiceClass annotation.");
                continue;
            }

            validateMethodExists(annotatedMethod, targetServiceClassMirror, targetMethodName);
        }
    }

    private void processQueryFileAnnotations(RoundEnvironment roundEnv) {
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(QueryFile.class);

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Found " + annotatedElements.size() + " elements annotated with @QueryFile in this round.");

        for (Element annotatedElement : annotatedElements) {
            if (annotatedElement.getKind() != ElementKind.METHOD) {
                continue;
            }

            ExecutableElement annotatedMethod = (ExecutableElement) annotatedElement;
            QueryFile queryFileAnnotation = annotatedMethod.getAnnotation(QueryFile.class);

            String queryFilePath = queryFileAnnotation.value();
            if (queryFilePath == null || queryFilePath.trim().isEmpty()) {
                error(annotatedMethod, "@QueryFile annotation must specify a file path");
                continue;
            }

            // Remove leading slash if present and validate file exists in resources
            String resourcePath = queryFilePath.startsWith("/") ? queryFilePath.substring(1) : queryFilePath;
            validateQueryFileExists(annotatedMethod, resourcePath);
        }
    }

    private void validateQueryFileExists(ExecutableElement method, String resourcePath) {
        boolean fileFound = false;
        String foundLocation = "";

        // Strategy 1: Try to find in class output resources (after resources are
        // processed)
        try {
            FileObject resource = processingEnv.getFiler().getResource(
                    StandardLocation.CLASS_OUTPUT, "", resourcePath);
            resource.openInputStream().close();
            fileFound = true;
            foundLocation = "class output";
        } catch (IOException e) {
            // File not found in class output, continue to next strategy
        }

        // Strategy 2: Try to find using class path resource (this works better in
        // practice)
        if (!fileFound) {
            try {
                // Try to load as a classpath resource
                ClassLoader classLoader = method.getClass().getClassLoader();
                if (classLoader.getResource(resourcePath) != null) {
                    fileFound = true;
                    foundLocation = "classpath";
                }
            } catch (Exception e) {
                // Continue to next strategy
            }
        }

        if (fileFound) {
            messager.printMessage(Diagnostic.Kind.NOTE,
                    "✓ Found query file: " + resourcePath + " in " + foundLocation +
                            " for method " + method.getSimpleName());
        } else {
            // Add more detailed debugging information
            String currentDir = System.getProperty("user.dir");
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Debug info - Current working directory: " + currentDir +
                            ", looking for: " + resourcePath);

            error(method,
                    "✗ Query file not found: %s. Make sure the file exists in src/main/resources/%s or src/test/resources/%s",
                    resourcePath, resourcePath, resourcePath);
        }
    }

    /**
     * Checks if a method with a matching signature (name and parameter types)
     * exists on the target class.
     */
    private void validateMethodExists(ExecutableElement sourceMethod, TypeMirror targetClass, String methodName) {
        TypeElement targetClassElement = (TypeElement) processingEnv.getTypeUtils().asElement(targetClass);

        List<? extends TypeMirror> sourceParamTypes = sourceMethod.getParameters().stream()
                .map(VariableElement::asType).toList();

        boolean methodFound = targetClassElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .anyMatch(targetMethod -> {
                    boolean nameMatches = targetMethod.getSimpleName().toString().equals(methodName);
                    if (!nameMatches) {
                        return false;
                    }

                    List<? extends VariableElement> targetParams = targetMethod.getParameters();
                    if (sourceParamTypes.size() != targetParams.size()) {
                        return false;
                    }

                    for (int i = 0; i < sourceParamTypes.size(); i++) {
                        TypeMirror sourceType = sourceParamTypes.get(i);
                        TypeMirror targetType = targetParams.get(i).asType();
                        if (!processingEnv.getTypeUtils().isSameType(sourceType, targetType)) {
                            return false;
                        }
                    }
                    return true;
                });

        if (!methodFound) {
            String paramsString = sourceParamTypes.stream()
                    .map(TypeMirror::toString)
                    .collect(Collectors.joining(", "));
            error(sourceMethod, "Method '%s(%s)' does not exist in class %s", methodName, paramsString,
                    targetClass.toString());
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
