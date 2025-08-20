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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        // Only print if this is not a subsequent round to reduce noise
        messager.printMessage(Diagnostic.Kind.NOTE, "ServiceMethodProcessor initialized.");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Skip processing if this is a subsequent round with no new files
        if (roundEnv.processingOver()) {
            return false;
        }

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

        // Strategy 1: Check the annotation processor classpath
        // (ANNOTATION_PROCESSOR_PATH)
        // This includes the application's compiled resources
        try {
            messager.printMessage(Diagnostic.Kind.NOTE, "Checking for query file: " + resourcePath);
            try (Stream<Path> file = Files.find(Path.of("."), 10,
                    (p, basicFileAttributes) -> p.getFileName().toString().equalsIgnoreCase(resourcePath))) {
                file.findFirst().ifPresent(f -> messager.printMessage(Diagnostic.Kind.NOTE,
                        "✓ Found query file: " + resourcePath + " in " + f.toAbsolutePath()));
            }
        } catch (IOException e) {
            // Continue to next strategy
        }

        try {

            FileObject resource = processingEnv.getFiler().getResource(
                    StandardLocation.ANNOTATION_PROCESSOR_PATH, "", resourcePath);
            resource.openInputStream().close();
            fileFound = true;
            foundLocation = "annotation processor path";
        } catch (IOException e) {
            // Continue to next strategy
        }

        // Strategy 2: Check class path - this includes compiled resources
        if (!fileFound) {
            try {
                FileObject resource = processingEnv.getFiler().getResource(
                        StandardLocation.CLASS_PATH, "", resourcePath);
                resource.openInputStream().close();
                fileFound = true;
                foundLocation = "class path";
            } catch (IOException e) {
                // Continue to next strategy
            }
        }

        // Strategy 3: Try class output (for cases where resources are already
        // processed)
        if (!fileFound) {
            try {
                FileObject resource = processingEnv.getFiler().getResource(
                        StandardLocation.CLASS_OUTPUT, "", resourcePath);
                resource.openInputStream().close();
                fileFound = true;
                foundLocation = "class output";
            } catch (IOException e) {
                // Continue to next strategy
            }
        }

        // Strategy 4: As a last resort, make it just a warning during development
        // since the file might not be compiled yet but exists in source
        if (!fileFound) {
            // During annotation processing, resource files might not be available yet
            // This is especially common in IDEs during development
            error(method, "Query file not found during compilation: " + resourcePath +
                    " for method " + method.getSimpleName() +
                    ". This might be normal if the resource hasn't been processed yet. " +
                    "Ensure the file exists in src/main/resources/" + resourcePath +
                    " or src/test/resources/" + resourcePath);
        } else {
            messager.printMessage(Diagnostic.Kind.NOTE,
                    "✓ Found query file: " + resourcePath + " in " + foundLocation +
                            " for method " + method.getSimpleName());
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
