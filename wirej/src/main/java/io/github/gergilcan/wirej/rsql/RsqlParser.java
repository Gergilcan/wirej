package io.github.gergilcan.wirej.rsql;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.database.DatabaseStatement;

@Component
public class RsqlParser {

  private static final String AND = " AND ";

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

    // Check if the where :filters is in first place in the original statement quer
    if (statement.getOriginalQuery().toLowerCase().contains("where :filters")) {
      return String.join(AND, whereClauses);
    }

    if (!statement.getOriginalQuery().toLowerCase().contains("where")) {
      return "WHERE " + String.join(AND, whereClauses);
    }

    return "AND " + String.join(AND, whereClauses);
  }

  private String parseWhereClause(String part, Class<?> entityClass, DatabaseStatement<?> statement,
      AtomicInteger parameterNumber) {
    var parts = part.replace("(", "").replace(")", "").split(",");
    List<String> whereClauses = new LinkedList<>();
    for (String clause : parts) {
      var whereClause = transformToSqlClause(clause, entityClass, statement, parameterNumber);

      if (whereClause != null) {
        whereClauses.add(whereClause);
      }
    }

    return !whereClauses.isEmpty() ? "(" + String.join(" OR ", whereClauses) + ")" : "";
  }

  public String parseSorting(String rsqlQuery, Class<?> entityClass) {
    var sortClauses = new LinkedList<String>();
    var multipleSortingParts = rsqlQuery.split(";");
    for (String part : multipleSortingParts) {
      var sortClause = transformToOrderByClause(part, entityClass);
      if (sortClause != null) {
        sortClauses.add(sortClause);
      }
    }

    return !sortClauses.isEmpty() ? "ORDER BY " + String.join(", ", sortClauses) : "";
  }

  private String transformToOrderByClause(String clause, Class<?> entityClass) {
    var operator = findOperator(clause);
    if (operator != null) {
      var clauseParts = clause.split(operator);
      var direction = clauseParts[1];
      if (direction.equals("DESC") || direction.equals("ASC")) {
        var columnName = findColumnNameFromAlias(clauseParts[0], entityClass);
        if (columnName != null) {
          return columnName + " " + direction;
        }
      }
    }

    return null;
  }

  private String transformToSqlClause(String clause, Class<?> entityClass, DatabaseStatement<?> statement,
      AtomicInteger parameterNumber) {
    var operator = findOperator(clause);
    if (operator != null) {
      var clauseParts = clause.split(operator);
      parameterNumber.incrementAndGet();
      var value = castType(clauseParts[1]);
      statement.setParameter("filter_value_" + parameterNumber, value);
      var fieldName = findColumnNameFromAlias(clauseParts[0], entityClass);

      if (fieldName != null) {
        // If its a timestamp field we need to check with a between that date and the
        // next day
        if (value instanceof Timestamp) {
          return "DATE(" + fieldName + ")" + getOperator(operator) + ":filter_value_" + parameterNumber;
        }

        return fieldName + (value == null ? " is null" : getOperator(operator) + ":filter_value_" + parameterNumber);
      }
    }

    return null;
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
    for (Field field : RSQLOperators.class.getDeclaredFields()) {
      try {
        var value = field.get(null).toString();
        if (clause.contains(value)) {
          return value;
        }
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }

    return null;
  }

  private String getOperator(String operator) {
    switch (operator) {
      case RSQLOperators.EQUAL:
        return " = ";
      case RSQLOperators.GREATER_THAN:
        return " > ";
      case RSQLOperators.GREATER_THAN_OR_EQUAL:
        return " >= ";
      case RSQLOperators.LESS_THAN_OR_EQUAL:
        return " <= ";
      case RSQLOperators.LESS_THAN:
        return " < ";
      default:
        break;
    }

    return null;
  }

  private String findColumnNameFromAlias(String alias, Class<?> entityClass) {
    String columnName = alias;
    try {
      var field = entityClass.getDeclaredField(alias);
      var columnAnnotation = field.getAnnotation(JsonAlias.class);
      if (columnAnnotation != null) {
        return columnAnnotation.value()[0];
      }

      var fields = entityClass.getDeclaredFields();
      for (var innerField : fields) {
        columnAnnotation = innerField.getAnnotation(JsonAlias.class);
        if (columnAnnotation != null && columnAnnotation.value()[0].equals(alias)) {
          return innerField.getName();
        }
      }
    } catch (NoSuchFieldException e) {
      // The alias is not a field of the entity class
      return alias;
    }

    return columnName;
  }
}
