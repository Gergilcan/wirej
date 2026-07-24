package io.github.gergilcan.wirej.repository;

import java.util.List;
import java.util.Map;

import io.github.gergilcan.wirej.annotations.StandardOperation;
import io.github.gergilcan.wirej.annotations.StandardOperationType;
import io.github.gergilcan.wirej.core.BatchPatchItem;
import io.github.gergilcan.wirej.core.PagedResult;
import io.github.gergilcan.wirej.core.RequestFilters;

/**
 * A standard CRUD repository surface a {@code @Repository} interface can
 * extend instead of writing the usual SQL files by hand. The SQL for these
 * operations is generated at compile time from the entity's own fields.
 *
 * The target table comes from {@code @WireJTable("table_name")} on the
 * entity, or {@code jakarta.persistence.Table(name = ...)} if already
 * present for JPA schema tooling. The primary key field is the one annotated
 * {@code @WireJId}, else the one annotated {@code jakarta.persistence.Id},
 * else the field named {@code id}.
 *
 * {@code update} performs a partial update: only the columns whose keys are
 * present in the changes map are set. Keys are the entity's field names (or
 * their {@code @JsonAlias}); unknown keys and the primary key itself are
 * rejected at runtime, so arbitrary strings can never reach the SQL text.
 *
 * {@code count} applies {@code filters} the same way {@code getAll} does but
 * returns the total number of matching rows instead of the rows themselves.
 * {@code getAll} here is unpaginated (it returns every matching row); when a
 * caller needs a page of data alongside that total count in one round trip,
 * use {@link PagedRepository}, whose {@code getAll} takes a
 * {@code RequestPagination} and returns a {@link PagedResult}.
 *
 * As with {@code getAll}, passing a literal {@code null} for {@code filters}
 * is not supported - pass a {@code RequestFilters} instance whose fields are
 * null/blank to mean "no filtering".
 *
 * {@code createBatch}/{@code updateBatch} insert/update every item in one
 * JDBC batch where possible; {@code updateBatch} groups items by their
 * distinct set of changed fields first (JDBC batching needs one fixed SQL
 * text per batch), so a call where every item changes the same field(s) is
 * one batch, while a fully heterogeneous call degrades to one statement per
 * item. Their returned array's order is not guaranteed to match the input.
 *
 * The extending interface can freely mix these inherited operations with its
 * own hand-written {@code @QueryFile} methods.
 */
public interface StandardRepository<T, ID> {
  @StandardOperation(StandardOperationType.GET)
  T get(ID id);

  @StandardOperation(StandardOperationType.GET_ALL)
  T[] getAll(RequestFilters filters);

  @StandardOperation(StandardOperationType.COUNT)
  Long count(RequestFilters filters);

  @StandardOperation(StandardOperationType.CREATE)
  T create(T entity);

  @StandardOperation(StandardOperationType.CREATE_BATCH)
  T[] createBatch(T[] entities);

  @StandardOperation(StandardOperationType.UPDATE)
  T update(ID id, Map<String, Object> changes);

  @StandardOperation(StandardOperationType.UPDATE_BATCH)
  T[] updateBatch(List<BatchPatchItem<ID>> items);

  @StandardOperation(StandardOperationType.DELETE)
  void delete(ID id);
}
