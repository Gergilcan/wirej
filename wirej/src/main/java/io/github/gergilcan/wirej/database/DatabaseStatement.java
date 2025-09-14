package io.github.gergilcan.wirej.database;

import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import io.github.gergilcan.PostgreSQLmapper.core.PostgresEntityMapper;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.rsql.RsqlParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

// nosemgrep
@Slf4j
@SuppressWarnings("unchecked")
public class DatabaseStatement<T> implements AutoCloseable {
  private static final String EXECUTING_QUERY_DEBUG_TEXT = "Executing query: ";
  private final Class<?> entityClass;
  private Connection connection;

  @Getter
  private String originalQuery;
  private String finalQuery;

  private PreparedStatement batchStatement;

  private final HashMap<String, Object> parameters = new HashMap<>();
  private final LinkedList<String> statementParameters = new LinkedList<>();
  private final PostgresEntityMapper entityMapper = new PostgresEntityMapper();
  private final long startTime;
  private final String fileName;

  public DatabaseStatement(String fileName, Class<?> entityClass, DataSource dataSource)
      throws IOException, SQLException {
    this.fileName = fileName;
    this.entityClass = entityClass;

    try (var file = getClass().getResourceAsStream(fileName)) {
      startTime = System.currentTimeMillis();

      if (file == null) {
        throw new IOException("File not found: " + fileName);
      }

      originalQuery = new String(file.readAllBytes());
      connection = dataSource.getConnection();
      log.debug("Statement and connection created: executed in {}ms", System.currentTimeMillis() - startTime);
    } catch (Exception e) {
      close();
      throw e;
    }
  }

  public DatabaseStatement(String fileName, RequestFilters filters, RequestPagination pagination,
      Class<?> entityClass, RsqlParser parser, DataSource dataSource) throws IOException, SQLException {
    this(fileName, entityClass, dataSource);

    if (pagination != null) {
      setParameter("initialPosition", pagination.getPageNumber() * pagination.getPageSize());
      setParameter("pageSize", pagination.getPageSize());
    }
    if (filters != null) {
      setParameter("search",
          filters.getSearch() != null && !filters.getSearch().isBlank() ? "%" + filters.getSearch().trim() + "%"
              : "%%");

      originalQuery = originalQuery.replace(":filters",
          filters.getFilters() != null && !filters.getFilters().isBlank()
              ? parser.parse(filters.getFilters(), entityClass, this)
              : "");
      originalQuery = originalQuery.replace(":sorting",
          filters.getSort() != null && !filters.getSort().isBlank()
              ? parser.parseSorting(filters.getSort(), entityClass)
              : "");
    }
  }

  public T getResult() throws SQLException {
      log.debug(EXECUTING_QUERY_DEBUG_TEXT + "{}", fileName);
      replaceParameters();
    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      var rs = statement.executeQuery();
      var results = (T[]) entityMapper.map(rs, entityClass.arrayType());
      return results.length > 0 ? results[0] : null;
    }
  }

  public T[] getResultList() throws SQLException {
      log.debug(EXECUTING_QUERY_DEBUG_TEXT + "{}", fileName);
    replaceParameters();
    var start = System.currentTimeMillis();
    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      var rs = statement.executeQuery();
      log.debug("Prepare statement: executed in {}ms", System.currentTimeMillis() - start);
      log.debug("Set statement parameters: executed in {}ms", System.currentTimeMillis() - start);
      return (T[]) entityMapper.map(rs, entityClass.arrayType());
    }
  }

  public void setParameter(String name, Object param) {
    parameters.put(name, param);
  }

  public boolean execute() throws SQLException {
      log.debug(EXECUTING_QUERY_DEBUG_TEXT + "{}", fileName);
    replaceParameters();
    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      return statement.execute();
    }
  }

  public void addBatch() throws SQLException {
    if (batchStatement == null) {
      replaceParameters();
      batchStatement = connection.prepareStatement(finalQuery, Statement.RETURN_GENERATED_KEYS);
    }
    setStatementParameters(batchStatement);
    batchStatement.addBatch();
  }

  public T[] executeBatch() throws SQLException {
    if (batchStatement != null) {
        log.debug(EXECUTING_QUERY_DEBUG_TEXT + "{}", fileName);
      batchStatement.executeBatch();
      if (entityClass != null && entityClass != Void.TYPE) {
        return (T[]) entityMapper.map(batchStatement.getGeneratedKeys(), entityClass.arrayType());
      }
    }
    if (entityClass != null && entityClass != Void.TYPE) {
      return (T[]) Array.newInstance(entityClass, 0);
    }
    return null;
  }

  private void replaceParameters() {
    var temporalQuery = originalQuery;
    Pattern pattern = Pattern.compile("(?<!:):\\w+");
    Matcher matcher = pattern.matcher(originalQuery);
    while (matcher.find()) {
      var parameterName = matcher.group().substring(1); // remove leading :
      statementParameters.add(parameterName);
      temporalQuery = temporalQuery.replaceAll(Pattern.quote(matcher.group()) + "\\b", "?");
    }
    finalQuery = temporalQuery;
  }

  private void setStatementParameters(PreparedStatement statement) throws SQLException {
    for (int i = 0; i < statementParameters.size(); i++) {
      var parameterName = statementParameters.get(i);
      if (!parameters.containsKey(parameterName)) {
        throw new SQLException("Missing parameter: " + parameterName);
      }
      statement.setObject(i + 1, parameters.get(parameterName));
    }
  }

  public T getSingleValue() throws SQLException {
      log.debug(EXECUTING_QUERY_DEBUG_TEXT + "{}", fileName);
    replaceParameters();
    try (var statement = connection.prepareStatement(finalQuery);
        var rs = statement.executeQuery()) {
      setStatementParameters(statement);
      if (rs.next()) {
        return (T) rs.getObject(1);
      }
      return null;
    }
  }

  public T[] getSingleValueList() throws SQLException {
      log.debug(EXECUTING_QUERY_DEBUG_TEXT + "{}", fileName);
    replaceParameters();
    var list = new ArrayList<T>();
    try (var statement = connection.prepareStatement(finalQuery);
        var rs = statement.executeQuery()) {
      setStatementParameters(statement);
      while (rs.next()) {
        list.add((T) rs.getObject(1));
      }
    }
    return list.toArray((T[]) Array.newInstance(entityClass, 0));
  }

  @Override
  public void close() {
    try {
      if (batchStatement != null) {
        batchStatement.clearBatch();
        batchStatement.close();
        batchStatement = null;
      }
    } catch (SQLException e) {
      log.warn("Error closing batchStatement", e);
    }
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
        connection = null;
      }
    } catch (SQLException e) {
      log.warn("Error closing connection", e);
    }
    log.debug("Query: {} executed in {}ms", fileName, System.currentTimeMillis() - startTime);
  }
}