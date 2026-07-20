package io.github.gergilcan.wirej.rsql;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.database.DatabaseStatement;
import io.github.gergilcan.wirej.exceptions.WireJException;

@Component
public class RsqlParser {

  private static final String AND = " AND ";
  private static final List<String> OPERATORS_BY_PRIORITY = List.of(
      RSQLOperators.EQUAL, RSQLOperators.NOT_EQUAL, RSQLOperators.GREATER_THAN_OR_EQUAL, RSQLOperators.GREATER_THAN,
      RSQLOperators.LESS_THAN_OR_EQUAL, RSQLOperators.LESS_THAN, RSQLOperators.IN, RSQLOperators.NOT_IN);
  private static final Map<String, String> SQL_OPERATORS = Map.of(
      RSQLOperators.EQUAL, " = ", RSQLOperators.NOT_EQUAL, " != ",
      RSQLOperators.GREATER_THAN, " > ", RSQLOperators.GREATER_THAN_OR_EQUAL, " >= ",
      RSQLOperators.LESS_THAN, " < ", RSQLOperators.LESS_THAN_OR_EQUAL, " <= ",
      RSQLOperators.IN, " LIKE ", RSQLOperators.NOT_IN, " NOT LIKE ");

  public String parse(String rsqlQuery, Class<?> entityClass, DatabaseStatement<?> statement) {
    AtomicInteger parameterNumber = new AtomicInteger(0);
    var queryParts = rsqlQuery.split(";");
    List<String> whereClauses = new LinkedList<>();
    for (String part : queryParts) {
      var whereToAdd = parseWhereClause(part, entityClass, statement, parameterNumber);
      if (!whereToAdd.isEmpty()) {
        whereClauses.add(whereToAdd);
      }
    }

    var joinedClauses = String.join(AND, whereClauses);
    var originalQuery = statement.getOriginalQuery().toLowerCase();
    if (originalQuery.contains("where :filters")) {
      return joinedClauses;
    }
    return (originalQuery.contains("where") ? "AND " : "WHERE ") + joinedClauses;
  }

  private String parseWhereClause(String part, Class<?> entityClass, DatabaseStatement<?> statement,
      AtomicInteger parameterNumber) {
    var parts = part.replace("(", "").replace(")", "").split(",");
    List<String> whereClauses = new LinkedList<>();
    for (String clause : parts) {
      whereClauses.add(transformToSqlClause(clause, entityClass, statement, parameterNumber));
    }

    return "(" + String.join(" OR ", whereClauses) + ")";
  }

  public String parseSorting(String rsqlQuery, Class<?> entityClass) {
    var sortClauses = new LinkedList<String>();
    var multipleSortingParts = rsqlQuery.split(";");
    for (String part : multipleSortingParts) {
      sortClauses.add(transformToOrderByClause(part, entityClass));
    }

    return "ORDER BY " + String.join(", ", sortClauses);
  }

  private String transformToOrderByClause(String clause, Class<?> entityClass) {
    var operator = findOperator(clause);
    if (operator == null) {
      throw new WireJException("Unrecognized sort clause (no supported operator found): '" + clause + "'");
    }

    var clauseParts = clause.split(operator);
    var direction = clauseParts[1];
    if (!direction.equals("DESC") && !direction.equals("ASC")) {
      throw new WireJException(
          "Invalid sort direction '" + direction + "' in clause '" + clause + "'; expected ASC or DESC");
    }

    return findColumnNameFromAlias(clauseParts[0], entityClass) + " " + direction;
  }

  private String transformToSqlClause(String clause, Class<?> entityClass, DatabaseStatement<?> statement,
      AtomicInteger parameterNumber) {
    var operator = findOperator(clause);
    if (operator == null) {
      throw new WireJException("Unrecognized filter clause (no supported operator found): '" + clause + "'");
    }

    var clauseParts = clause.split(operator);
    parameterNumber.incrementAndGet();
    var value = castType(clauseParts[1]);
    if (operator.equals(RSQLOperators.IN) || operator.equals(RSQLOperators.NOT_IN)) {
      value = "%" + value + "%";
    }
    statement.setParameter("filter_value_" + parameterNumber, value);
    var fieldName = findColumnNameFromAlias(clauseParts[0], entityClass);

    // If its a timestamp field we need to check with a between that date and the
    // next day
    if (value instanceof Timestamp) {
      return "DATE(" + fieldName + ")" + getOperator(operator) + ":filter_value_" + parameterNumber;
    }

    return fieldName + (value == null ? " is null" : getOperator(operator) + ":filter_value_" + parameterNumber);
  }

  private Object castType(String clauseValue) {
    if (clauseValue.equals("true") || clauseValue.equals("false")) {
      return Boolean.parseBoolean(clauseValue);
    }

    if (clauseValue.equals("null")) {
      return null;
    }
    // I want to check if the clauseValue is a date of format yyyy-MM-dd to
    // timestamp
    if (clauseValue.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return Timestamp.valueOf(clauseValue + " 00:00:00");
    }

    try {
      return new BigDecimal(clauseValue);
    } catch (NumberFormatException e) {
      try {
        return LocalDateTime.parse(clauseValue, DateTimeFormatter.ISO_DATE_TIME);
      } catch (Exception ex) {
        return clauseValue;
      }
    }
  }

  private String findOperator(String clause) {
    return OPERATORS_BY_PRIORITY.stream().filter(clause::contains).findFirst().orElse(null);
  }

  private String getOperator(String operator) {
    return SQL_OPERATORS.get(operator);
  }

  private String findColumnNameFromAlias(String alias, Class<?> entityClass) {
    try {
      JsonAlias columnAnnotation = entityClass.getDeclaredField(alias).getAnnotation(JsonAlias.class);
      if (columnAnnotation != null) {
        return columnAnnotation.value()[0];
      }
    } catch (NoSuchFieldException e) {
    }

    for (var field : entityClass.getDeclaredFields()) {
      JsonAlias columnAnnotation = field.getAnnotation(JsonAlias.class);
      if (columnAnnotation != null && columnAnnotation.value()[0].equals(alias)) {
        return field.getName();
      }
    }

    return alias;
  }
}
