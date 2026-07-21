package io.github.gergilcan.wirej.rsql;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonAlias;

import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.RSQLParserException;
import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;

import io.github.gergilcan.wirej.database.DatabaseStatement;
import io.github.gergilcan.wirej.exceptions.WireJException;

@Component
public class RsqlParser {

  private static final ComparisonOperator EQUAL = new ComparisonOperator("==");
  private static final ComparisonOperator NOT_EQUAL = new ComparisonOperator("!=");
  private static final ComparisonOperator GREATER_THAN = new ComparisonOperator(">");
  private static final ComparisonOperator GREATER_THAN_OR_EQUAL = new ComparisonOperator(">=");
  private static final ComparisonOperator LESS_THAN = new ComparisonOperator("<");
  private static final ComparisonOperator LESS_THAN_OR_EQUAL = new ComparisonOperator("<=");
  // WireJ's =in=/=out= are single-value LIKE-style wildcard matches, not RSQL's
  // usual multivalue (v1,v2) lists, so they're registered as single-value here.
  private static final ComparisonOperator IN = new ComparisonOperator("=in=");
  private static final ComparisonOperator NOT_IN = new ComparisonOperator("=out=");

  private static final Set<ComparisonOperator> FILTER_OPERATORS = Set.of(
      EQUAL, NOT_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL, IN, NOT_IN);

  private static final Map<ComparisonOperator, String> SQL_OPERATORS = Map.of(
      EQUAL, " = ", NOT_EQUAL, " != ",
      GREATER_THAN, " > ", GREATER_THAN_OR_EQUAL, " >= ",
      LESS_THAN, " < ", LESS_THAN_OR_EQUAL, " <= ",
      IN, " LIKE ", NOT_IN, " NOT LIKE ");

  private static final RSQLParser FILTER_PARSER = new RSQLParser(FILTER_OPERATORS);

  // Sort clauses ("id==DESC") aren't RSQL - they're WireJ's own field==DIRECTION
  // convention - so they're still matched by plain substring search.
  private static final List<String> SORT_OPERATOR_SYMBOLS = List.of(
      "==", "!=", ">=", ">", "<=", "<", "=in=", "=out=");

  public String parse(String rsqlQuery, Class<?> entityClass, DatabaseStatement<?> statement) {
    Node rootNode = parseNode(rsqlQuery);
    String whereClause = rootNode.accept(new SqlVisitor(entityClass, statement, new AtomicInteger(0)));

    var originalQuery = statement.getOriginalQuery().toLowerCase();
    if (originalQuery.contains("where :filters")) {
      return whereClause;
    }
    return (originalQuery.contains("where") ? "AND " : "WHERE ") + whereClause;
  }

  private Node parseNode(String rsqlQuery) {
    try {
      return FILTER_PARSER.parse(rsqlQuery);
    } catch (RSQLParserException e) {
      throw new WireJException("Unrecognized filter clause (no supported operator found): '" + rsqlQuery + "'", e);
    }
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
    var operator = findSortOperator(clause);
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

  private String findSortOperator(String clause) {
    return SORT_OPERATOR_SYMBOLS.stream().filter(clause::contains).findFirst().orElse(null);
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

  private final class SqlVisitor implements RSQLVisitor<String, Void> {
    private final Class<?> entityClass;
    private final DatabaseStatement<?> statement;
    private final AtomicInteger parameterNumber;

    SqlVisitor(Class<?> entityClass, DatabaseStatement<?> statement, AtomicInteger parameterNumber) {
      this.entityClass = entityClass;
      this.statement = statement;
      this.parameterNumber = parameterNumber;
    }

    @Override
    public String visit(AndNode node, Void param) {
      return combine(node, " AND ");
    }

    @Override
    public String visit(OrNode node, Void param) {
      return combine(node, " OR ");
    }

    private String combine(LogicalNode node, String joiner) {
      return "(" + node.getChildren().stream().map(child -> child.accept(this))
          .collect(Collectors.joining(joiner)) + ")";
    }

    @Override
    public String visit(ComparisonNode node, Void param) {
      ComparisonOperator operator = node.getOperator();
      String sqlOperator = SQL_OPERATORS.get(operator);
      if (sqlOperator == null) {
        throw new WireJException("Unrecognized filter operator: '" + operator + "'");
      }

      parameterNumber.incrementAndGet();
      Object value = castType(node.getArguments().get(0));
      if (operator.equals(IN) || operator.equals(NOT_IN)) {
        value = "%" + value + "%";
      }
      statement.setParameter("filter_value_" + parameterNumber, value);
      var fieldName = findColumnNameFromAlias(node.getSelector(), entityClass);

      // If its a timestamp field we need to check with a between that date and the
      // next day
      if (value instanceof Timestamp) {
        return "DATE(" + fieldName + ")" + sqlOperator + ":filter_value_" + parameterNumber;
      }

      return fieldName + (value == null ? " is null" : sqlOperator + ":filter_value_" + parameterNumber);
    }
  }
}
