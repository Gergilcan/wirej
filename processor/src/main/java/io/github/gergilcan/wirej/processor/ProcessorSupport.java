package io.github.gergilcan.wirej.processor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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

    static List<VariableElement> persistableFields(TypeElement entity) {
        return entity.getEnclosedElements().stream()
                .filter(member -> member.getKind() == ElementKind.FIELD)
                .map(VariableElement.class::cast)
                .filter(field -> !field.getModifiers().contains(Modifier.STATIC))
                .toList();
    }

    // The jakarta annotations are matched by qualified name via mirrors rather
    // than by Class so neither the processor nor the annotations module needs a
    // JPA dependency - same approach as @JsonAlias and Spring's @Repository.
    private static final String WIREJ_TABLE = "io.github.gergilcan.wirej.annotations.WireJTable";
    private static final String JAKARTA_TABLE = "jakarta.persistence.Table";
    private static final String WIREJ_ID = "io.github.gergilcan.wirej.annotations.WireJId";
    private static final String JAKARTA_ID = "jakarta.persistence.Id";

    static Optional<String> findTableName(TypeElement entity, Elements elements) {
        return findAnnotationStringValue(entity, WIREJ_TABLE, "value", elements)
                .or(() -> findAnnotationStringValue(entity, JAKARTA_TABLE, "name", elements)
                        .filter(name -> !name.isBlank()));
    }

    static Optional<VariableElement> findPrimaryKeyField(TypeElement entity) {
        List<VariableElement> fields = persistableFields(entity);
        return fields.stream().filter(field -> hasAnnotation(field, WIREJ_ID)).findFirst()
                .or(() -> fields.stream().filter(field -> hasAnnotation(field, JAKARTA_ID)).findFirst())
                .or(() -> fields.stream().filter(field -> field.getSimpleName().contentEquals("id")).findFirst());
    }

    static boolean hasAnnotation(Element element, String qualifiedName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
            if (annotationType.getQualifiedName().contentEquals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> findAnnotationStringValue(Element element, String annotationName,
            String memberName, Elements elements) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
            if (!annotationType.getQualifiedName().contentEquals(annotationName)) {
                continue;
            }
            for (var entry : elements.getElementValuesWithDefaults(mirror).entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals(memberName)) {
                    return Optional.of(String.valueOf(entry.getValue().getValue()));
                }
            }
        }
        return Optional.empty();
    }
}
