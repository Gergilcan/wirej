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

import io.github.gergilcan.PostgreSQLmapper.core.PostgresEntityMapper;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.rsql.RsqlParser;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

// nosemgrep
@Slf4j
@SuppressWarnings("unchecked")
public class DatabaseStatement<T> {
  private static final String EXECUTING_QUERY_DEBUG_TEXT = "Executing query: ";
  private Class<?> entityClass;
  private Connection connection;

  @Getter
  private String originalQuery;
  private String finalQuery;

  private PreparedStatement batchStatement;

  private HashMap<String, Object> parameters = new HashMap<>();
  private LinkedList<String> statementParameters = new LinkedList<>();
  private PostgresEntityMapper entityMapper = new PostgresEntityMapper();
  private long startTime;
  private String fileName;

  public DatabaseStatement(String fileName, ConnectionHandler connectionHandler) throws IOException, SQLException {
    this(fileName, null, connectionHandler);
  }

  public DatabaseStatement(String fileName, Class<?> entityClass, ConnectionHandler connectionHandler)
      throws IOException, SQLException {
    this.fileName = fileName;
    this.entityClass = entityClass;

    try (var file = getClass().getResourceAsStream(fileName)) {
      startTime = System.currentTimeMillis();

      if (file == null) {
        throw new IOException("File not found: " + fileName);
      }

      originalQuery = new String(file.readAllBytes());
      connection = connectionHandler.getConnection();
      log.debug("Statement and connection created: executed in {}ms", System.currentTimeMillis() - startTime);
    }
  }

  public DatabaseStatement(String fileName, RequestFilters filters, Class<?> entityClass,
      ConnectionHandler connectionHandler) throws IOException, SQLException {
    this(fileName, filters, null, entityClass, null, connectionHandler);
  }

  public DatabaseStatement(String fileName, RequestFilters filters, Class<?> entityClass, RsqlParser parser,
      ConnectionHandler connectionHandler) throws IOException, SQLException {
    this(fileName, filters, null, entityClass, parser, connectionHandler);
  }

  public DatabaseStatement(String fileName, RequestFilters filters, RequestPagination pagination,
      Class<?> entityClass, RsqlParser parser, ConnectionHandler connectionHandler) throws IOException, SQLException {
    this(fileName, entityClass, connectionHandler);

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
    log.debug(EXECUTING_QUERY_DEBUG_TEXT + fileName);
    replaceParameters();
    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      var rs = statement.executeQuery();
      var results = (T[]) entityMapper.map(rs, entityClass.arrayType());
      return results.length > 0 ? results[0] : null;
    } finally {
      close();
    }
  }

  public T[] getResultList() throws SQLException {
    log.debug(EXECUTING_QUERY_DEBUG_TEXT + fileName);
    replaceParameters();
    var start = System.currentTimeMillis();
    try (var statement = connection.prepareStatement(finalQuery)) {
      log.debug("Prepare statement: executed in " + (System.currentTimeMillis() - start) + "ms");
      start = System.currentTimeMillis();
      setStatementParameters(statement);
      log.debug("Set statement parameters: executed in " + (System.currentTimeMillis() - start) + "ms");
      start = System.currentTimeMillis();
      var rs = statement.executeQuery();
      log.debug("Execute query: executed in " + (System.currentTimeMillis() - start) + "ms");
      start = System.currentTimeMillis();
      return (T[]) entityMapper.map(rs, entityClass.arrayType());
    } finally {
      close();
    }
  }

  public void setParameter(String name, Object param) {
    parameters.put(name, param);
  }

  public boolean execute() throws SQLException {
    log.debug(EXECUTING_QUERY_DEBUG_TEXT + fileName);
    replaceParameters();
    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      return statement.execute();
    } finally {
      close();
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
    try {
      if (batchStatement != null) {
        log.debug(EXECUTING_QUERY_DEBUG_TEXT + fileName);
        batchStatement.executeBatch();
        if (entityClass != null && entityClass != Void.TYPE) {
          return (T[]) entityMapper.map(batchStatement.getGeneratedKeys(), entityClass.arrayType());
        }
      }
    } finally {
      if (batchStatement != null) {
        batchStatement.close();
      }
      close();
    }

    if (entityClass != null && entityClass != Void.TYPE) {
      return (T[]) Array.newInstance(entityClass, 0);
    }

    return null;
  }

  private void close() throws SQLException {
    connection.close();
    log.debug("Query: " + fileName + " executed in " + (System.currentTimeMillis() - startTime) + "ms");
  }

  public void closeStatement() throws SQLException {
    if (batchStatement != null) {
      batchStatement.close();
      batchStatement = null;
    }
    if (connection != null && !connection.isClosed()) {
      connection.close();
      connection = null;
    }
  }

  private void replaceParameters() {
    var temporalQuery = originalQuery;
    Pattern pattern = Pattern.compile(":\\w*");
    Matcher matcher = pattern.matcher(originalQuery);
    while (matcher.find()) {
      var parameterName = matcher.group().replace(":", "");
      statementParameters.add(parameterName);
      temporalQuery = temporalQuery.replaceAll(matcher.group() + "\\b", "?");
    }
    finalQuery = temporalQuery;
  }

  private void setStatementParameters(PreparedStatement statement) throws SQLException {
    for (int i = 0; i < statementParameters.size(); i++) {
      var parameterName = statementParameters.get(i);
      statement.setObject(i + 1, parameters.get(parameterName));
    }
  }

  public T getSingleValue() throws SQLException {
    log.debug(EXECUTING_QUERY_DEBUG_TEXT + fileName);
    replaceParameters();

    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      var rs = statement.executeQuery();
      rs.next();
      return (T) rs.getObject(1);
    } finally {
      close();
    }
  }

  public T[] getSingleValueList() throws SQLException {
    log.debug(EXECUTING_QUERY_DEBUG_TEXT + fileName);
    replaceParameters();
    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      var list = new ArrayList<T>();
      var rs = statement.executeQuery();
      while (rs.next()) {
        list.add((T) rs.getObject(1));
      }
      return list.toArray((T[]) Array.newInstance(entityClass, 0));
    } catch (SQLException e) {
      return (T[]) Array.newInstance(entityClass, 0);
    } finally {
      close();
    }

  }
}
