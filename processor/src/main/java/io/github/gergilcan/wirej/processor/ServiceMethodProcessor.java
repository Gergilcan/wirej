package io.github.gergilcan.wirej.processor;

import com.google.auto.service.AutoService;
import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Discovery is driven from @ServiceClass interfaces rather than from
     * @ServiceMethod methods, because a controller interface can inherit all of
     * its @ServiceMethod methods from a generic base (e.g. StandardRestRepository)
     * without declaring any of its own - grouping by getEnclosingElement() would
     * never see such a controller at all. Elements.getAllMembers() walks the full
     * interface hierarchy for us, and Types.asMemberOf() resolves each inherited
     * method's type variables (T, ID, ...) to the concrete types the controller
     * interface was declared with.
     */
    private void processServiceMethodAnnotations(RoundEnvironment roundEnv) {
        Set<? extends Element> serviceClassElements = roundEnv.getElementsAnnotatedWith(ServiceClass.class);

        messager.printMessage(Diagnostic.Kind.NOTE,
                "Found " + serviceClassElements.size() + " elements annotated with @ServiceClass in this round.");

        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        for (Element element : serviceClassElements) {
            if (element.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            TypeElement controllerInterface = (TypeElement) element;

            if (!controllerInterface.getTypeParameters().isEmpty()) {
                error(controllerInterface,
                        "@ServiceClass interfaces must not declare their own type parameters: '%s'",
                        controllerInterface.getSimpleName());
                continue;
            }

            ServiceClass serviceClassAnnotation = controllerInterface.getAnnotation(ServiceClass.class);
            TypeMirror targetServiceClassMirror = getServiceClassValue(serviceClassAnnotation);
            if (targetServiceClassMirror == null) {
                error(controllerInterface, "Could not resolve target service class for @ServiceClass annotation.");
                continue;
            }

            DeclaredType controllerType = (DeclaredType) controllerInterface.asType();
            List<ExecutableElement> serviceMethods = elementUtils.getAllMembers(controllerInterface).stream()
                    .filter(member -> member.getKind() == ElementKind.METHOD)
                    .map(ExecutableElement.class::cast)
                    .filter(method -> method.getAnnotation(ServiceMethod.class) != null)
                    .toList();

            List<ControllerImplGenerator.ResolvedMethod> resolvedMethods = new ArrayList<>();
            boolean allResolved = true;

            for (ExecutableElement annotatedMethod : serviceMethods) {
                Element diagnosticAnchor = annotatedMethod.getEnclosingElement().equals(controllerInterface)
                        ? annotatedMethod
                        : controllerInterface;

                ServiceMethod serviceMethodAnnotation = annotatedMethod.getAnnotation(ServiceMethod.class);
                String targetMethodName = serviceMethodAnnotation.value();

                if (targetMethodName == null || targetMethodName.trim().isEmpty()) {
                    targetMethodName = annotatedMethod.getSimpleName().toString();
                    messager.printMessage(Diagnostic.Kind.NOTE,
                            "Using method name '" + targetMethodName + "' for @ServiceMethod on " +
                                    annotatedMethod.getSimpleName());
                }

                ExecutableType substitutedType = (ExecutableType) typeUtils.asMemberOf(controllerType,
                        annotatedMethod);

                Optional<ExecutableElement> matchingMethod = findMatchingMethod(substitutedType,
                        targetServiceClassMirror, targetMethodName);

                if (matchingMethod.isEmpty()) {
                    String paramsString = substitutedType.getParameterTypes().stream()
                            .map(TypeMirror::toString)
                            .collect(Collectors.joining(", "));
                    error(diagnosticAnchor, "Method '%s(%s)' does not exist in class %s", targetMethodName,
                            paramsString, targetServiceClassMirror.toString());
                    allResolved = false;
                    continue;
                }

                checkReturnTypeCompatible(diagnosticAnchor, substitutedType.getReturnType(), matchingMethod.get(),
                        targetMethodName, targetServiceClassMirror);
                resolvedMethods.add(new ControllerImplGenerator.ResolvedMethod(annotatedMethod, substitutedType,
                        matchingMethod.get()));
            }

            if (allResolved) {
                controllerImplGenerator.generate(controllerInterface, targetServiceClassMirror, resolvedMethods);
            }
        }

        validateNoOrphanedServiceMethods(roundEnv);
    }

    /**
     * Discovery above only visits @ServiceMethod methods reachable from an
     * @ServiceClass interface, so a method on a plain interface that forgot
     * @ServiceClass entirely would otherwise go unvalidated. Generic interfaces
     * (like StandardRestRepository) are exempt: they're reusable bases meant to be
     * extended, not concrete controllers, so they're never expected to carry
     * @ServiceClass themselves.
     */
    private void validateNoOrphanedServiceMethods(RoundEnvironment roundEnv) {
        Set<TypeElement> orphanedInterfaces = roundEnv.getElementsAnnotatedWith(ServiceMethod.class).stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(element -> (TypeElement) element.getEnclosingElement())
                .filter(enclosing -> enclosing.getAnnotation(ServiceClass.class) == null)
                .filter(enclosing -> enclosing.getTypeParameters().isEmpty())
                .collect(Collectors.toSet());

        for (TypeElement enclosing : orphanedInterfaces) {
            error(enclosing, "The class/interface containing @ServiceMethod must be annotated with @ServiceClass.");
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

    private static final List<String> SOURCE_RESOURCE_ROOTS = List.of("src/main/resources", "src/test/resources");
    // Compatibility fallback for when the module root can't be resolved at all
    // (some non-Maven/non-Gradle build). Deliberately NOT used as a fallback
    // once source directories are checkable — see the javadoc below for why
    // that would defeat the whole point of this method.
    private static final List<StandardLocation> CLASSPATH_LOOKUP_LOCATIONS = List.of(
            StandardLocation.ANNOTATION_PROCESSOR_PATH, StandardLocation.CLASS_PATH, StandardLocation.CLASS_OUTPUT);

    /**
     * Checks the module's own src/main/resources and src/test/resources,
     * resolved directly on disk (see {@link #resolveModuleRoot()}), and treats
     * that as authoritative whenever it's available.
     *
     * This used to check the classpath (CLASS_OUTPUT / CLASS_PATH /
     * ANNOTATION_PROCESSOR_PATH) instead, which has two problems, both
     * confirmed empirically, not just in theory:
     * <ul>
     * <li>CLASS_OUTPUT (target/classes) is never pruned of orphaned files by a
     * plain {@code mvn compile} — delete or rename a query file and recompile
     * without {@code clean}, and the stale copy from the previous build is
     * still found there.
     * <li>CLASS_PATH isn't safe either: on an incremental (non-clean)
     * recompile, Maven adds the module's own target/classes to the effective
     * javac classpath so already-compiled-but-unchanged classes stay visible
     * to newly-recompiled ones - so it can leak the exact same staleness.
     * </ul>
     * Resolving straight from source sidesteps both, and as a side effect is
     * also what makes this fire as a live, design-time diagnostic in an IDE's
     * incremental build (the same way the @ServiceMethod checks already do -
     * those only ever inspect Java symbols, which the compiler always has in
     * memory regardless of build tool/timing, whereas resource files depend on
     * a resources-copy step having already run first).
     *
     * Falls back to the old classpath-scanning behavior only when the module
     * root can't be determined at all (e.g. some non-Maven/non-Gradle build) -
     * better than nothing for an unusual setup, but not relied on otherwise.
     */
    private boolean validateQueryFileExists(ExecutableElement method, String resourcePath) {
        File moduleRoot = resolveModuleRoot();
        boolean canCheckSource = moduleRoot != null && SOURCE_RESOURCE_ROOTS.stream()
                .anyMatch(root -> new File(moduleRoot, root).isDirectory());

        if (canCheckSource) {
            for (String sourceRoot : SOURCE_RESOURCE_ROOTS) {
                File candidate = new File(moduleRoot, sourceRoot + File.separator
                        + resourcePath.replace('/', File.separatorChar));
                if (candidate.isFile()) {
                    messager.printMessage(Diagnostic.Kind.NOTE,
                            "✓ Found query file: " + resourcePath + " at " + candidate + " for method "
                                    + method.getSimpleName());
                    return true;
                }
            }

            error(method, "Query file not found: " + resourcePath + " for method " + method.getSimpleName() +
                    ". Checked " + moduleRoot + "/src/main/resources/" + resourcePath + " and " +
                    moduleRoot + "/src/test/resources/" + resourcePath + ".");
            return false;
        }

        for (StandardLocation location : CLASSPATH_LOOKUP_LOCATIONS) {
            try (var in = processingEnv.getFiler().getResource(location, "", resourcePath).openInputStream()) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                        "✓ Found query file: " + resourcePath + " in " + location + " for method "
                                + method.getSimpleName());
                return true;
            } catch (IOException e) {
            }
        }

        error(method, "Query file not found: " + resourcePath +
                " for method " + method.getSimpleName() +
                ". Module root could not be resolved, so only the compile classpath was checked (not " +
                "src/main/resources/" + resourcePath + " or src/test/resources/" + resourcePath + " directly).");
        return false;
    }

    /**
     * Best-effort resolution of the current module's base directory, by asking
     * the Filer for a (possibly nonexistent) CLASS_OUTPUT resource and walking
     * up from its URI: .../target/classes/<probe> -> target -> module root.
     * Returns null if that URI can't be turned into a filesystem path (some
     * non-Maven/non-Gradle Filer implementations don't support it), in which
     * case callers fall back to the original classpath-only behavior.
     */
    private File resolveModuleRoot() {
        try {
            var probe = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "",
                    "wirej-module-root-probe");
            Path outputDir = Paths.get(probe.toUri()).getParent(); // .../target/classes
            Path buildDir = outputDir == null ? null : outputDir.getParent(); // .../target
            Path moduleRoot = buildDir == null ? null : buildDir.getParent(); // module root
            return moduleRoot == null ? null : moduleRoot.toFile();
        } catch (Exception e) {
            // IOException from getResource itself, or any RuntimeException from a Filer
            // that can't produce a real filesystem URI for CLASS_OUTPUT - either way,
            // this is a best-effort optimization, not something worth failing the build
            // over.
            return null;
        }
    }

    private Optional<ExecutableElement> findMatchingMethod(ExecutableType sourceMethodType, TypeMirror targetClass,
            String methodName) {
        TypeElement targetClassElement = (TypeElement) processingEnv.getTypeUtils().asElement(targetClass);
        Types typeUtils = processingEnv.getTypeUtils();

        List<? extends TypeMirror> sourceParamTypes = sourceMethodType.getParameterTypes();

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

    private void checkReturnTypeCompatible(Element diagnosticAnchor, TypeMirror substitutedReturnType,
            ExecutableElement targetMethod, String methodName, TypeMirror targetClass) {
        if (substitutedReturnType.getKind() != TypeKind.DECLARED) {
            return;
        }

        List<? extends TypeMirror> typeArguments = ((DeclaredType) substitutedReturnType).getTypeArguments();
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
            error(diagnosticAnchor,
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
