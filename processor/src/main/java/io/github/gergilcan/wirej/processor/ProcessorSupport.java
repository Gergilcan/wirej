package io.github.gergilcan.wirej.processor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

final class ProcessorSupport {
    private static final Set<String> BASIC_TYPE_NAMES = Set.of(
            "java.lang.String", "java.lang.Boolean", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Short", "java.lang.Byte",
            "java.math.BigDecimal", "java.sql.Timestamp", "java.time.LocalDateTime", "java.util.Date");

    private static final String JSON_ALIAS = "com.fasterxml.jackson.annotation.JsonAlias";

    private ProcessorSupport() {
    }

    static boolean isType(TypeMirror mirror, String qualifiedName) {
        if (mirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeElement element = (TypeElement) ((DeclaredType) mirror).asElement();
        return element.getQualifiedName().contentEquals(qualifiedName);
    }

    static boolean isBasicType(TypeMirror mirror) {
        if (mirror.getKind().isPrimitive()) {
            return true;
        }
        if (mirror.getKind() == TypeKind.ARRAY) {
            TypeMirror component = ((ArrayType) mirror).getComponentType();
            return component.getKind().isPrimitive() || isType(component, "java.lang.String");
        }
        return BASIC_TYPE_NAMES.stream().anyMatch(name -> isType(mirror, name));
    }

    static String toSnakeCase(String raw) {
        return raw.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    static Optional<String> findJsonAlias(Element element, Elements elements) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
            if (!annotationType.getQualifiedName().contentEquals(JSON_ALIAS)) {
                continue;
            }
            for (var entry : elements.getElementValuesWithDefaults(mirror).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("value")) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) entry.getValue()
                        .getValue();
                if (!values.isEmpty()) {
                    return Optional.of(String.valueOf(values.get(0).getValue()));
                }
            }
        }
        return Optional.empty();
    }

    static String resolveParameterName(Element element, String rawName, Elements elements) {
        return findJsonAlias(element, elements).orElseGet(() -> toSnakeCase(rawName));
    }
}
