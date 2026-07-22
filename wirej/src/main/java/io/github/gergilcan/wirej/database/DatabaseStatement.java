package io.github.gergilcan.wirej.database;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
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
  private ConnectionHandler connectionHandler;

  public DatabaseStatement(String fileName, ConnectionHandler connectionHandler) throws IOException, SQLException {
    this(fileName, null, connectionHandler);
  }

  public DatabaseStatement(String fileName, Class<?> entityClass, ConnectionHandler connectionHandler)
      throws IOException, SQLException {
    this.entityClass = entityClass;
    loadQueryFile(fileName);
    openConnection(connectionHandler);
  }

  public DatabaseStatement(String fileName, RequestFilters filters, RequestPagination pagination,
      Class<?> entityClass, RsqlParser parser, ConnectionHandler connectionHandler) throws IOException, SQLException {
    this.entityClass = entityClass;
    loadQueryFile(fileName);
    applyRequestOptions(filters, pagination, parser);
    openConnection(connectionHandler);
  }

  private DatabaseStatement() {
  }

  /**
   * Creates a statement from literal query text instead of a classpath .sql
   * file - the entry point for compile-time-generated SQL (StandardRepository
   * operations), where the query is baked into the generated source as a
   * string and there is no file to load. {@code queryName} is used purely for
   * logging and error messages, taking the place a file name has elsewhere.
   */
  public static <T> DatabaseStatement<T> forGeneratedQuery(String queryText, String queryName,
      RequestFilters filters, RequestPagination pagination, Class<?> entityClass, RsqlParser parser,
      ConnectionHandler connectionHandler) {
    DatabaseStatement<T> statement = new DatabaseStatement<>();
    statement.entityClass = entityClass;
    statement.fileName = queryName;
    statement.startTime = System.currentTimeMillis();
    statement.originalQuery = queryText;
    statement.applyRequestOptions(filters, pagination, parser);
    statement.openConnection(connectionHandler);
    return statement;
  }

  private void applyRequestOptions(RequestFilters filters, RequestPagination pagination, RsqlParser parser) {
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

  private static final ConcurrentHashMap<String, String> QUERY_FILE_CACHE = new ConcurrentHashMap<>();

  private void loadQueryFile(String fileName) throws IOException {
    this.fileName = fileName;
    startTime = System.currentTimeMillis();
    try {
      originalQuery = QUERY_FILE_CACHE.computeIfAbsent(fileName, DatabaseStatement::readQueryFile);
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static String readQueryFile(String fileName) {
    try (var file = DatabaseStatement.class.getResourceAsStream(fileName)) {
      if (file == null) {
        throw new UncheckedIOException(new IOException("File not found: " + fileName));
      }
      return new String(file.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void openConnection(ConnectionHandler connectionHandler) {
    this.connectionHandler = connectionHandler;
    connection = connectionHandler.getConnection();
    log.debug("Statement and connection created: executed in {}ms", System.currentTimeMillis() - startTime);
  }

  public T getResult() throws SQLException {
    return runQuery(statement -> {
      var rs = statement.executeQuery();
      var results = (T[]) entityMapper.map(rs, entityClass.arrayType());
      return results.length > 0 ? results[0] : null;
    });
  }

  public T[] getResultList() throws SQLException {
    return runQuery(statement -> (T[]) entityMapper.map(statement.executeQuery(), entityClass.arrayType()));
  }

  public void setParameter(String name, Object param) {
    parameters.put(name, param);
  }

  public boolean execute() throws SQLException {
    return runQuery(PreparedStatement::execute);
  }

  private <R> R runQuery(SqlFunction<R> action) throws SQLException {
    log.debug("{}{}", EXECUTING_QUERY_DEBUG_TEXT, fileName);
    replaceParameters();
    try (var statement = connection.prepareStatement(finalQuery)) {
      setStatementParameters(statement);
      return action.apply(statement);
    } finally {
      close();
    }
  }

  @FunctionalInterface
  private interface SqlFunction<R> {
    R apply(PreparedStatement statement) throws SQLException;
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
        log.debug("{}{}", EXECUTING_QUERY_DEBUG_TEXT, fileName);
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

  private void close() {
    connectionHandler.releaseConnection(connection);
    log.debug("Query: {} executed in {}ms", fileName, System.currentTimeMillis() - startTime);
  }

  public void closeStatement() throws SQLException {
    if (batchStatement != null) {
      batchStatement.close();
      batchStatement = null;
    }
    if (connection != null) {
      connectionHandler.releaseConnection(connection);
      connection = null;
    }
  }

  public static void closeQuietly(DatabaseStatement<?> statement) {
    if (statement == null) {
      return;
    }
    try {
      statement.closeStatement();
    } catch (SQLException e) {
      log.warn("Failed to close database statement after an earlier failure", e);
    }
  }

  private static final Pattern PARAMETER_PATTERN = Pattern.compile("(?<!:):(?!:)([a-zA-Z_]\\w*)");

  private void replaceParameters() {
    statementParameters.clear();
    Matcher matcher = PARAMETER_PATTERN.matcher(originalQuery);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      statementParameters.add(matcher.group(1));
      matcher.appendReplacement(result, "?");
    }
    matcher.appendTail(result);
    finalQuery = result.toString();
  }

  private void setStatementParameters(PreparedStatement statement) throws SQLException {
    for (int i = 0; i < statementParameters.size(); i++) {
      var parameterName = statementParameters.get(i);
      statement.setObject(i + 1, toJdbcValue(parameters.get(parameterName)));
    }
  }

  private static Object toJdbcValue(Object value) {
    return value instanceof Enum<?> enumValue ? enumValue.name() : value;
  }

  public T getSingleValue() throws SQLException {
    return runQuery(statement -> {
      var rs = statement.executeQuery();
      rs.next();
      return (T) rs.getObject(1);
    });
  }

  public T[] getSingleValueList() throws SQLException {
    return runQuery(statement -> {
      try {
        var list = new ArrayList<T>();
        var rs = statement.executeQuery();
        while (rs.next()) {
          list.add((T) rs.getObject(1));
        }
        return list.toArray((T[]) Array.newInstance(entityClass, 0));
      } catch (SQLException e) {
        return (T[]) Array.newInstance(entityClass, 0);
      }
    });
  }
}
