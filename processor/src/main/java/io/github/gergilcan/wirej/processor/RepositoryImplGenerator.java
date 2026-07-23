package io.github.gergilcan.wirej.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
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
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.annotations.QueryOperation;
import io.github.gergilcan.wirej.annotations.StandardOperationType;

/**
 * Generates a real implementing class for each {@code @Repository} interface
 * with {@code @QueryFile} methods, replacing the runtime
 * {@code RepositoryInvocationHandler} dynamic proxy. Every branch that
 * {@code RepositoryInvocationHandler} used to resolve per-call via
 * reflection (operation dispatch, entity class, parameter names) is knowable
 * from the method's compile-time signature and annotation values, so it's
 * resolved once here instead of on every invocation.
 *
 * Also generates the SQL and bodies for {@code StandardRepository} CRUD
 * operations inherited by the interface: the query text is derived from the
 * entity's own fields at compile time and baked into the generated source as
 * a string literal (via {@code DatabaseStatement.forGeneratedQuery}), so no
 * .sql file exists for these operations.
 */
final class RepositoryImplGenerator {
    private static final String REQUEST_FILTERS = "io.github.gergilcan.wirej.core.RequestFilters";
    private static final String REQUEST_PAGINATION = "io.github.gergilcan.wirej.core.RequestPagination";
    private static final String CLASS_TYPE = "java.lang.Class";

    record StandardMethod(ExecutableElement method, ExecutableType type, StandardOperationType operation) {
    }

    record StandardCrud(TypeMirror entityType, TypeMirror idType, String tableName, String pkFieldName,
            String pkColumn, List<StandardMethod> methods) {
    }

    private final Filer filer;
    private final Messager messager;
    private final Elements elements;

    RepositoryImplGenerator(Filer filer, Messager messager, Elements elements) {
        this.filer = filer;
        this.messager = messager;
        this.elements = elements;
    }

    void generate(TypeElement repositoryInterface, List<ExecutableElement> queryFileMethods,
            StandardCrud standardCrud) {
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

        for (ExecutableElement method : queryFileMethods) {
            typeBuilder.addMethod(buildMethod(method));
        }

        if (standardCrud != null) {
            if (standardCrud.methods().stream().anyMatch(m -> m.operation() == StandardOperationType.UPDATE
                    || m.operation() == StandardOperationType.UPDATE_BATCH)) {
                typeBuilder.addField(buildUpdateColumnsField(standardCrud));
            }
            for (StandardMethod standardMethod : standardCrud.methods()) {
                typeBuilder.addMethod(buildStandardMethod(standardMethod, standardCrud));
            }
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

    private MethodSpec buildStandardMethod(StandardMethod standardMethod, StandardCrud crud) {
        ExecutableElement method = standardMethod.method();
        ExecutableType methodType = standardMethod.type();
        String methodName = method.getSimpleName().toString();

        MethodSpec.Builder method_ = MethodSpec.methodBuilder(methodName)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(methodType.getReturnType()));
        List<? extends VariableElement> parameters = method.getParameters();
        List<? extends TypeMirror> parameterTypes = methodType.getParameterTypes();
        for (int i = 0; i < parameters.size(); i++) {
            method_.addParameter(TypeName.get(parameterTypes.get(i)), parameters.get(i).getSimpleName().toString());
        }

        CodeBlock body = switch (standardMethod.operation()) {
            case GET -> buildGetBody(parameters, crud, methodName);
            case GET_ALL -> buildGetAllBody(parameters, crud, methodName);
            case COUNT -> buildCountBody(parameters, crud, methodName);
            case GET_PAGE -> buildGetPageBody(parameters, crud, methodName);
            case CREATE -> buildCreateBody(parameters, crud, methodName);
            case CREATE_BATCH -> buildCreateBatchBody(parameters, crud, methodName);
            case UPDATE -> buildUpdateBody(parameters, crud, methodName);
            case UPDATE_BATCH -> buildUpdateBatchBody(parameters, crud, methodName);
            case DELETE -> buildDeleteBody(parameters, crud, methodName);
        };
        method_.addCode(body);
        return method_.build();
    }

    private CodeBlock buildGetBody(List<? extends VariableElement> parameters, StandardCrud crud, String methodName) {
        String idParam = parameters.get(0).getSimpleName().toString();
        TypeName entityName = TypeName.get(crud.entityType());
        String sql = "SELECT * FROM " + crud.tableName() + " WHERE " + crud.pkColumn() + " = :" + crud.pkColumn();

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T stmt = null",
                ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, entityName));
        body.beginControlFlow("try");
        body.addStatement("stmt = $T.forGeneratedQuery($S, $S, null, null, $T.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, sql, queryName(crud, methodName),
                entityName);
        body.addStatement("stmt.setParameter($S, $L)", crud.pkColumn(), idParam);
        body.addStatement("return stmt.getResult()");
        addStandardCatches(body, crud, methodName);
        return body.build();
    }

    private CodeBlock buildGetAllBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String filtersParam = parameters.get(0).getSimpleName().toString();
        TypeName entityName = TypeName.get(crud.entityType());
        // Plain getAll is unpaginated - pagination lives on PagedRepository.getAll
        // (GET_PAGE). No OFFSET/FETCH clause, and null pagination passed through.
        String sql = "SELECT * FROM " + crud.tableName() + " :filters :sorting";

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T stmt = null",
                ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, entityName));
        body.beginControlFlow("try");
        body.addStatement("stmt = $T.forGeneratedQuery($S, $S, $L, null, $T.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, sql, queryName(crud, methodName),
                filtersParam, entityName);
        body.addStatement("return stmt.getResultList()");
        addStandardCatches(body, crud, methodName);
        return body.build();
    }

    private CodeBlock buildCountBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String filtersParam = parameters.get(0).getSimpleName().toString();
        TypeName longName = TypeName.get(Long.class);
        TypeName entityName = TypeName.get(crud.entityType());
        String sql = "SELECT count(*) FROM " + crud.tableName() + " :filters";

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T stmt = null", ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, longName));
        body.beginControlFlow("try");
        // entityClass here is only used by RsqlParser to resolve filter selectors
        // against the entity's fields/@JsonAlias - it must be the real entity type,
        // not Long, even though Long is what this statement's result is cast to.
        body.addStatement("stmt = $T.forGeneratedQuery($S, $S, $L, null, $T.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, sql, queryName(crud, methodName),
                filtersParam, entityName);
        body.addStatement("return stmt.getSingleValue()");
        addStandardCatches(body, crud, methodName);
        return body.build();
    }

    /**
     * Builds a page of data plus the unpaginated total count in one method. Runs
     * both queries inline rather than delegating to sibling getAll/count methods,
     * so it works whether or not this interface also exposes a plain getAll -
     * PagedRepository's getAll IS this body, with no array-returning sibling to
     * call.
     */
    private CodeBlock buildGetPageBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String filtersParam = parameters.get(0).getSimpleName().toString();
        String paginationParam = parameters.get(1).getSimpleName().toString();
        TypeName entityName = TypeName.get(crud.entityType());
        TypeName longName = TypeName.get(Long.class);
        String selectSql = "SELECT * FROM " + crud.tableName()
                + " :filters :sorting OFFSET :initialPosition ROWS FETCH NEXT :pageSize ROWS ONLY";
        String countSql = "SELECT count(*) FROM " + crud.tableName() + " :filters";

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T dataStmt = null", ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, entityName));
        body.addStatement("$T countStmt = null", ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, longName));
        body.beginControlFlow("try");
        body.addStatement("dataStmt = $T.forGeneratedQuery($S, $S, $L, $L, $T.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, selectSql,
                queryName(crud, methodName) + ".data", filtersParam, paginationParam, entityName);
        body.addStatement("$T data = dataStmt.getResultList()", ArrayTypeName.of(entityName));
        body.addStatement("countStmt = $T.forGeneratedQuery($S, $S, $L, null, $T.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, countSql,
                queryName(crud, methodName) + ".count", filtersParam, entityName);
        body.addStatement("$T totalCount = countStmt.getSingleValue()", longName);
        body.addStatement("return new $T<>(data, totalCount)", WireJTypes.PAGED_RESULT);
        body.nextControlFlow("catch ($T e)", WireJTypes.SQL_EXCEPTION);
        body.addStatement("$T.closeQuietly(dataStmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("$T.closeQuietly(countStmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw new $T($S + e.getMessage(), e)", WireJTypes.WIREJ_EXCEPTION,
                "Query failed for repository method '" + methodName + "' (generated query: "
                        + queryName(crud, methodName) + "): ");
        body.nextControlFlow("catch ($T e)", WireJTypes.RUNTIME_EXCEPTION);
        body.addStatement("$T.closeQuietly(dataStmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("$T.closeQuietly(countStmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw e");
        body.endControlFlow();
        return body.build();
    }

    private CodeBlock buildCreateBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String entityParam = parameters.get(0).getSimpleName().toString();
        List<VariableElement> fields = ProcessorSupport
                .persistableFields((TypeElement) ((DeclaredType) crud.entityType()).asElement());

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (VariableElement field : fields) {
            String column = ProcessorSupport.resolveParameterName(field, field.getSimpleName().toString(), elements);
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(column);
            values.append(":").append(column);
        }
        String sql = "INSERT INTO " + crud.tableName() + " (" + columns + ") VALUES (" + values + ")";

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T stmt = null",
                ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, TypeName.get(Void.class)));
        body.beginControlFlow("try");
        body.addStatement("stmt = $T.forGeneratedQuery($S, $S, null, null, void.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, sql, queryName(crud, methodName));
        body.addStatement("$T.bindObjectFields($L, stmt)", WireJTypes.PARAMETER_BINDER, entityParam);
        body.addStatement("stmt.execute()");
        body.addStatement("return $L", entityParam);
        addStandardCatches(body, crud, methodName);
        return body.build();
    }

    private CodeBlock buildUpdateBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String idParam = parameters.get(0).getSimpleName().toString();
        String changesParam = parameters.get(1).getSimpleName().toString();

        CodeBlock.Builder body = CodeBlock.builder();
        body.beginControlFlow("if ($L == null || $L.isEmpty())", changesParam, changesParam);
        body.addStatement("throw new $T($S)", WireJTypes.WIREJ_EXCEPTION,
                "update(...) requires at least one field in the changes map");
        body.endControlFlow();
        body.addStatement("$T setClause = new $T()", StringBuilder.class, StringBuilder.class);
        body.addStatement("$T<String, Object> boundValues = new $T<>()", Map.class, HashMap.class);
        body.addStatement("int index = 0");
        body.beginControlFlow("for ($T<String, Object> entry : $L.entrySet())", Map.Entry.class, changesParam);
        // Column names are concatenated into SQL text, so they may only ever come from
        // the compile-time UPDATE_COLUMNS map - never from the incoming key itself.
        body.addStatement("String column = UPDATE_COLUMNS.get(entry.getKey())");
        body.beginControlFlow("if (column == null)");
        body.addStatement("throw new $T($S + entry.getKey() + $S)", WireJTypes.WIREJ_EXCEPTION,
                "Unknown field in update payload: '", "'");
        body.endControlFlow();
        body.addStatement("String paramName = $S + index++", "update_value_");
        body.beginControlFlow("if (setClause.length() > 0)");
        body.addStatement("setClause.append($S)", ", ");
        body.endControlFlow();
        body.addStatement("setClause.append(column).append($S).append(paramName)", " = :");
        body.addStatement("boundValues.put(paramName, entry.getValue())");
        body.endControlFlow();

        body.addStatement("$T stmt = null",
                ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, TypeName.get(Void.class)));
        body.beginControlFlow("try");
        body.addStatement("stmt = $T.forGeneratedQuery($S + setClause + $S, $S, null, null, void.class, "
                + "this.rsqlParser, this.connectionHandler)", WireJTypes.DATABASE_STATEMENT,
                "UPDATE " + crud.tableName() + " SET ", " WHERE " + crud.pkColumn() + " = :" + crud.pkColumn(),
                queryName(crud, methodName));
        body.addStatement("boundValues.forEach(stmt::setParameter)");
        body.addStatement("stmt.setParameter($S, $L)", crud.pkColumn(), idParam);
        body.addStatement("stmt.execute()");
        body.addStatement("return this.get($L)", idParam);
        addStandardCatches(body, crud, methodName);
        return body.build();
    }

    private CodeBlock buildCreateBatchBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String entitiesParam = parameters.get(0).getSimpleName().toString();
        List<VariableElement> fields = ProcessorSupport
                .persistableFields((TypeElement) ((DeclaredType) crud.entityType()).asElement());

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (VariableElement field : fields) {
            String column = ProcessorSupport.resolveParameterName(field, field.getSimpleName().toString(), elements);
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(column);
            values.append(":").append(column);
        }
        String sql = "INSERT INTO " + crud.tableName() + " (" + columns + ") VALUES (" + values + ")";

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T stmt = null",
                ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, TypeName.get(Void.class)));
        body.beginControlFlow("try");
        body.addStatement("stmt = $T.forGeneratedQuery($S, $S, null, null, void.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, sql, queryName(crud, methodName));
        body.beginControlFlow("for ($T item : $L)", TypeName.get(crud.entityType()), entitiesParam);
        body.addStatement("$T.bindObjectFields(item, stmt)", WireJTypes.PARAMETER_BINDER);
        body.addStatement("stmt.addBatch()");
        body.endControlFlow();
        body.addStatement("stmt.executeBatch()");
        body.addStatement("return $L", entitiesParam);
        addStandardCatches(body, crud, methodName);
        return body.build();
    }

    /**
     * JDBC batching needs one fixed prepared-statement text per batch, but two
     * items in one call may change different fields. Items are grouped by
     * their distinct sorted set of changed-field names first - one statement/
     * one batch per group - so a call where every item changes the same
     * field(s) is a single batch, while a fully heterogeneous call degrades to
     * one statement per item. There is no per-row RETURNING for UPDATE
     * batches, so the updated rows are re-fetched afterwards with a single
     * {@code WHERE <pk> IN (...)} query over every id in the call; that
     * result's order is not guaranteed to match the input order.
     */
    private CodeBlock buildUpdateBatchBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String itemsParam = parameters.get(0).getSimpleName().toString();
        TypeName entityName = TypeName.get(crud.entityType());
        TypeName idName = TypeName.get(crud.idType()).box();
        TypeName batchPatchItemOfId = ParameterizedTypeName.get(WireJTypes.BATCH_PATCH_ITEM, idName);
        TypeName setOfString = ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class));
        TypeName listOfBatchPatchItem = ParameterizedTypeName.get(ClassName.get(List.class), batchPatchItemOfId);
        TypeName groupsType = ParameterizedTypeName.get(ClassName.get(Map.class), setOfString, listOfBatchPatchItem);

        CodeBlock.Builder body = CodeBlock.builder();
        body.beginControlFlow("if ($L == null || $L.isEmpty())", itemsParam, itemsParam);
        body.addStatement("throw new $T($S)", WireJTypes.WIREJ_EXCEPTION,
                "updateBatch(...) requires at least one item");
        body.endControlFlow();

        body.addStatement("$T groups = new $T<>()", groupsType, ClassName.get(LinkedHashMap.class));
        body.addStatement("$T<$T> allIds = new $T<>()", ClassName.get(List.class), idName,
                ClassName.get(ArrayList.class));
        body.beginControlFlow("for ($T item : $L)", batchPatchItemOfId, itemsParam);
        body.beginControlFlow("if (item.changes() == null || item.changes().isEmpty())");
        body.addStatement("throw new $T($S)", WireJTypes.WIREJ_EXCEPTION,
                "updateBatch(...) requires at least one field in each item's changes map");
        body.endControlFlow();
        body.beginControlFlow("for ($T key : item.changes().keySet())", ClassName.get(String.class));
        body.beginControlFlow("if (!UPDATE_COLUMNS.containsKey(key))");
        body.addStatement("throw new $T($S + key + $S)", WireJTypes.WIREJ_EXCEPTION,
                "Unknown field in update payload: '", "'");
        body.endControlFlow();
        body.endControlFlow();
        body.addStatement("groups.computeIfAbsent(new $T<>(item.changes().keySet()), key -> new $T<>()).add(item)",
                ClassName.get(TreeSet.class), ClassName.get(ArrayList.class));
        body.endControlFlow();

        body.beginControlFlow("for ($T<$T, $T> group : groups.entrySet())", Map.Entry.class, setOfString,
                listOfBatchPatchItem);
        body.addStatement("$T<$T> orderedKeys = new $T<>(group.getKey())", ClassName.get(List.class),
                ClassName.get(String.class), ClassName.get(ArrayList.class));
        body.addStatement("$T setClause = new $T()", StringBuilder.class, StringBuilder.class);
        body.addStatement("$T<$T> paramNames = new $T<>()", ClassName.get(List.class), ClassName.get(String.class),
                ClassName.get(ArrayList.class));
        body.beginControlFlow("for (int i = 0; i < orderedKeys.size(); i++)");
        body.addStatement("String paramName = $S + i", "batch_value_");
        body.addStatement("paramNames.add(paramName)");
        body.beginControlFlow("if (setClause.length() > 0)");
        body.addStatement("setClause.append($S)", ", ");
        body.endControlFlow();
        body.addStatement("setClause.append(UPDATE_COLUMNS.get(orderedKeys.get(i))).append($S).append(paramName)",
                " = :");
        body.endControlFlow();

        body.addStatement("$T stmt = null",
                ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, TypeName.get(Void.class)));
        body.beginControlFlow("try");
        body.addStatement("stmt = $T.forGeneratedQuery($S + setClause + $S, $S, null, null, void.class, "
                + "this.rsqlParser, this.connectionHandler)", WireJTypes.DATABASE_STATEMENT,
                "UPDATE " + crud.tableName() + " SET ", " WHERE " + crud.pkColumn() + " = :" + crud.pkColumn(),
                queryName(crud, methodName));
        body.beginControlFlow("for ($T item : group.getValue())", batchPatchItemOfId);
        body.beginControlFlow("for (int i = 0; i < orderedKeys.size(); i++)");
        body.addStatement("stmt.setParameter(paramNames.get(i), item.changes().get(orderedKeys.get(i)))");
        body.endControlFlow();
        body.addStatement("stmt.setParameter($S, item.id())", crud.pkColumn());
        body.addStatement("stmt.addBatch()");
        body.addStatement("allIds.add(item.id())");
        body.endControlFlow();
        body.addStatement("stmt.executeBatch()");
        addStandardCatches(body, crud, methodName);
        body.endControlFlow();

        body.addStatement("$T inClause = new $T()", StringBuilder.class, StringBuilder.class);
        body.beginControlFlow("for (int i = 0; i < allIds.size(); i++)");
        body.beginControlFlow("if (i > 0)");
        body.addStatement("inClause.append($S)", ", ");
        body.endControlFlow();
        body.addStatement("inClause.append($S).append(i)", ":batch_pk_");
        body.endControlFlow();

        body.addStatement("$T selectStmt = null", ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, entityName));
        body.beginControlFlow("try");
        body.addStatement(
                "selectStmt = $T.forGeneratedQuery($S + inClause + $S, $S, null, null, $T.class, this.rsqlParser, "
                        + "this.connectionHandler)",
                WireJTypes.DATABASE_STATEMENT,
                "SELECT * FROM " + crud.tableName() + " WHERE " + crud.pkColumn() + " IN (",
                ")",
                queryName(crud, methodName) + ".select",
                entityName);
        body.beginControlFlow("for (int i = 0; i < allIds.size(); i++)");
        body.addStatement("selectStmt.setParameter($S + i, allIds.get(i))", "batch_pk_");
        body.endControlFlow();
        body.addStatement("return selectStmt.getResultList()");
        body.nextControlFlow("catch ($T e)", WireJTypes.SQL_EXCEPTION);
        body.addStatement("$T.closeQuietly(selectStmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw new $T($S + e.getMessage(), e)", WireJTypes.WIREJ_EXCEPTION,
                "Query failed for repository method '" + methodName + "' (generated query: "
                        + queryName(crud, methodName) + ".select): ");
        body.nextControlFlow("catch ($T e)", WireJTypes.RUNTIME_EXCEPTION);
        body.addStatement("$T.closeQuietly(selectStmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw e");
        body.endControlFlow();
        return body.build();
    }

    private CodeBlock buildDeleteBody(List<? extends VariableElement> parameters, StandardCrud crud,
            String methodName) {
        String idParam = parameters.get(0).getSimpleName().toString();
        String sql = "DELETE FROM " + crud.tableName() + " WHERE " + crud.pkColumn() + " = :" + crud.pkColumn();

        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T stmt = null",
                ParameterizedTypeName.get(WireJTypes.DATABASE_STATEMENT, TypeName.get(Void.class)));
        body.beginControlFlow("try");
        body.addStatement("stmt = $T.forGeneratedQuery($S, $S, null, null, void.class, this.rsqlParser, "
                + "this.connectionHandler)", WireJTypes.DATABASE_STATEMENT, sql, queryName(crud, methodName));
        body.addStatement("stmt.setParameter($S, $L)", crud.pkColumn(), idParam);
        body.addStatement("stmt.execute()");
        addStandardCatches(body, crud, methodName);
        return body.build();
    }

    private void addStandardCatches(CodeBlock.Builder body, StandardCrud crud, String methodName) {
        body.nextControlFlow("catch ($T e)", WireJTypes.SQL_EXCEPTION);
        body.addStatement("$T.closeQuietly(stmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw new $T($S + e.getMessage(), e)", WireJTypes.WIREJ_EXCEPTION,
                "Query failed for repository method '" + methodName + "' (generated query: "
                        + queryName(crud, methodName) + "): ");
        body.nextControlFlow("catch ($T e)", WireJTypes.RUNTIME_EXCEPTION);
        body.addStatement("$T.closeQuietly(stmt)", WireJTypes.DATABASE_STATEMENT);
        body.addStatement("throw e");
        body.endControlFlow();
    }

    private String queryName(StandardCrud crud, String methodName) {
        return ((DeclaredType) crud.entityType()).asElement().getSimpleName() + "." + methodName;
    }

    /**
     * Maps every acceptable update-payload key (the entity's field names and
     * their @JsonAlias spellings) to the already-sanitized column name,
     * excluding the primary key. Only values from this map are ever
     * concatenated into SQL text, so an arbitrary incoming key can never
     * reach the query - and the id column can't be overwritten through the
     * partial-update path.
     */
    private FieldSpec buildUpdateColumnsField(StandardCrud crud) {
        List<VariableElement> fields = ProcessorSupport
                .persistableFields((TypeElement) ((DeclaredType) crud.entityType()).asElement());

        Map<String, String> updateColumns = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            String fieldName = field.getSimpleName().toString();
            if (fieldName.equals(crud.pkFieldName())) {
                continue;
            }
            String column = ProcessorSupport.resolveParameterName(field, fieldName, elements);
            updateColumns.put(fieldName, column);
            ProcessorSupport.findJsonAlias(field, elements).ifPresent(alias -> updateColumns.put(alias, column));
        }

        CodeBlock.Builder initializer = CodeBlock.builder().add("$T.ofEntries(", ClassName.get(Map.class));
        boolean first = true;
        for (var entry : updateColumns.entrySet()) {
            if (!first) {
                initializer.add(", ");
            }
            initializer.add("$T.entry($S, $S)", ClassName.get(Map.class), entry.getKey(), entry.getValue());
            first = false;
        }
        initializer.add(")");

        return FieldSpec.builder(ParameterizedTypeName.get(Map.class, String.class, String.class),
                "UPDATE_COLUMNS", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(initializer.build())
                .build();
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
