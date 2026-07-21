package io.github.gergilcan.wirej.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.annotations.QueryOperation;

/**
 * Generates a real implementing class for each {@code @Repository} interface
 * with {@code @QueryFile} methods, replacing the runtime
 * {@code RepositoryInvocationHandler} dynamic proxy. Every branch that
 * {@code RepositoryInvocationHandler} used to resolve per-call via
 * reflection (operation dispatch, entity class, parameter names) is knowable
 * from the method's compile-time signature and annotation values, so it's
 * resolved once here instead of on every invocation.
 */
final class RepositoryImplGenerator {
    private static final String REQUEST_FILTERS = "io.github.gergilcan.wirej.core.RequestFilters";
    private static final String REQUEST_PAGINATION = "io.github.gergilcan.wirej.core.RequestPagination";
    private static final String CLASS_TYPE = "java.lang.Class";

    private final Filer filer;
    private final Messager messager;
    private final Elements elements;

    RepositoryImplGenerator(Filer filer, Messager messager, Elements elements) {
        this.filer = filer;
        this.messager = messager;
        this.elements = elements;
    }

    void generate(TypeElement repositoryInterface, List<ExecutableElement> methods) {
        ClassName interfaceName = ClassName.get(repositoryInterface);
        String implName = interfaceName.simpleName() + "Impl";

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(TypeName.get(repositoryInterface.asType()));

        for (AnnotationMirror mirror : repositoryInterface.getAnnotationMirrors()) {
            typeBuilder.addAnnotation(AnnotationSpec.get(mirror));
        }

        typeBuilder.addField(WireJTypes.CONNECTION_HANDLER, "connectionHandler", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addField(WireJTypes.RSQL_PARSER, "rsqlParser", Modifier.PRIVATE, Modifier.FINAL);
        typeBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(WireJTypes.CONNECTION_HANDLER, "connectionHandler")
                .addParameter(WireJTypes.RSQL_PARSER, "rsqlParser")
                .addStatement("this.connectionHandler = connectionHandler")
                .addStatement("this.rsqlParser = rsqlParser")
                .build());

        for (ExecutableElement method : methods) {
            typeBuilder.addMethod(buildMethod(method));
        }

        try {
            JavaFile.builder(interfaceName.packageName(), typeBuilder.build()).build().writeTo(filer);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write generated repository implementation: " + e.getMessage(), repositoryInterface);
        }
    }

    private MethodSpec buildMethod(ExecutableElement method) {
        QueryFile queryFile = method.getAnnotation(QueryFile.class);
        String fileName = queryFile.value();
        boolean isBatch = queryFile.isBatch();
        QueryOperation operation = queryFile.operation();
        String methodName = method.getSimpleName().toString();
        TypeMirror returnType = method.getReturnType();

        VariableElement filtersParam = null;
        VariableElement paginationParam = null;
        VariableElement classParam = null;
        List<VariableElement> normalParams = new ArrayList<>();
        for (VariableElement parameter : method.getParameters()) {
            if (ProcessorSupport.isType(parameter.asType(), REQUEST_FILTERS)) {
                filtersParam = parameter;
            } else if (ProcessorSupport.isType(parameter.asType(), REQUEST_PAGINATION)) {
                paginationParam = parameter;
            } else if (ProcessorSupport.isType(parameter.asType(), CLASS_TYPE)) {
                classParam = parameter;
            } else {
                normalParams.add(parameter);
            }
        }

        CodeBlock entityClassExpr = resolveEntityClassExpr(returnType, classParam);
        TypeName statementGeneric = resolveStatementGeneric(returnType);
        TypeName statementType = ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, statementGeneric);

        boolean isSelect = operation != QueryOperation.AUTO ? operation == QueryOperation.SELECT
                : (methodName.startsWith("get") || methodName.startsWith("find"));
        boolean isCount = operation != QueryOperation.AUTO ? operation == QueryOperation.COUNT
                : methodName.toLowerCase().contains("count");

        MethodSpec.Builder method_ = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(returnType));
        for (VariableElement parameter : method.getParameters()) {
            method_.addParameter(TypeName.get(parameter.asType()), parameter.getSimpleName().toString());
        }

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T stmt = null", statementType);
        body.beginControlFlow("try");
        body.addStatement("stmt = new $T<>($S, $L, $L, $L, this.rsqlParser, this.connectionHandler)",
                WireJTypes.DATABASE_STATEMENT, fileName,
                filtersParam != null ? filtersParam.getSimpleName().toString() : "null",
                paginationParam != null ? paginationParam.getSimpleName().toString() : "null",
                entityClassExpr);

        if (isBatch && !isSelect && !isCount) {
            addBatchBindings(body, normalParams);
        } else {
            addScalarBindings(body, normalParams);
        }

        addDispatch(body, isSelect, isCount, isBatch, returnType);

        body.nextControlFlow("catch ($T | $T e)", WireJTypes.IO_EXCEPTION, WireJTypes.SQL_EXCEPTION);
        body.addStatement("$T.closeQuietly(stmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw new $T($S + $L.getSimpleName() + $S + e.getMessage(), e)",
                WireJTypes.WIREJ_EXCEPTION,
                "Query failed for repository method '" + methodName + "' (query file: " + fileName + ", entity: ",
                entityClassExpr, "): ");
        body.nextControlFlow("catch ($T e)", WireJTypes.RUNTIME_EXCEPTION);
        body.addStatement("$T.closeQuietly(stmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw e");
        body.endControlFlow();

        method_.addCode(body.build());
        return method_.build();
    }

    private void addScalarBindings(CodeBlock.Builder body, List<VariableElement> normalParams) {
        for (VariableElement parameter : normalParams) {
            String name = parameter.getSimpleName().toString();
            String resolvedName = ProcessorSupport.resolveParameterName(parameter, name, elements);
            if (ProcessorSupport.isBasicType(parameter.asType())) {
                body.addStatement("stmt.setParameter($S, $L)", resolvedName, name);
            } else {
                body.addStatement("$T.bindObjectFields($L, stmt)", WireJTypes.PARAMETER_BINDER, name);
            }
        }
    }

    private void addBatchBindings(CodeBlock.Builder body, List<VariableElement> normalParams) {
        for (VariableElement parameter : normalParams) {
            if (parameter.asType().getKind() != TypeKind.ARRAY) {
                continue;
            }
            String arrayName = parameter.getSimpleName().toString();
            String resolvedName = ProcessorSupport.resolveParameterName(parameter, arrayName, elements);
            TypeMirror componentType = ((ArrayType) parameter.asType()).getComponentType();
            String itemVar = arrayName + "Item";

            body.beginControlFlow("for ($T $L : $L)", TypeName.get(componentType), itemVar, arrayName);
            if (ProcessorSupport.isBasicType(componentType)) {
                body.addStatement("stmt.setParameter($S, $L)", resolvedName, itemVar);
            } else {
                body.addStatement("$T.bindObjectFields($L, stmt)", WireJTypes.PARAMETER_BINDER, itemVar);
            }
            body.addStatement("stmt.addBatch()");
            body.endControlFlow();
        }
    }

    private void addDispatch(CodeBlock.Builder body, boolean isSelect, boolean isCount, boolean isBatch,
            TypeMirror returnType) {
        if (isSelect) {
            if (returnType.getKind() == TypeKind.ARRAY) {
                TypeMirror component = ((ArrayType) returnType).getComponentType();
                body.addStatement(ProcessorSupport.isBasicType(component) ? "return stmt.getSingleValueList()"
                        : "return stmt.getResultList()");
            } else if (ProcessorSupport.isType(returnType, "java.lang.Long")
                    || ProcessorSupport.isType(returnType, "java.lang.Integer")
                    || ProcessorSupport.isType(returnType, "java.lang.Boolean")) {
                body.addStatement("return stmt.getSingleValue()");
            } else {
                body.addStatement("return stmt.getResult()");
            }
        } else if (isCount) {
            body.addStatement("return stmt.getSingleValue()");
        } else if (!isBatch) {
            if (returnType.getKind() == TypeKind.VOID) {
                body.addStatement("stmt.execute()");
            } else {
                body.addStatement("return stmt.getResult()");
            }
        } else {
            if (returnType.getKind() == TypeKind.VOID) {
                body.addStatement("stmt.executeBatch()");
            } else {
                body.addStatement("return stmt.executeBatch()");
            }
        }
    }

    private CodeBlock resolveEntityClassExpr(TypeMirror returnType, VariableElement classParam) {
        if (classParam != null) {
            return CodeBlock.of("$L", classParam.getSimpleName().toString());
        }
        if (returnType.getKind() == TypeKind.ARRAY) {
            TypeMirror component = ((ArrayType) returnType).getComponentType();
            return CodeBlock.of("$T.class", TypeName.get(component));
        }
        if (returnType.getKind() == TypeKind.VOID) {
            return CodeBlock.of("void.class");
        }
        return CodeBlock.of("$T.class", TypeName.get(returnType));
    }

    private TypeName resolveStatementGeneric(TypeMirror returnType) {
        if (returnType.getKind() == TypeKind.ARRAY) {
            return TypeName.get(((ArrayType) returnType).getComponentType()).box();
        }
        if (returnType.getKind() == TypeKind.VOID) {
            return TypeName.get(Void.class);
        }
        return TypeName.get(returnType).box();
    }
}
