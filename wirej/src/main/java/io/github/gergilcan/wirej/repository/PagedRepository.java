package io.github.gergilcan.wirej.repository;

import io.github.gergilcan.wirej.annotations.StandardOperation;
import io.github.gergilcan.wirej.annotations.StandardOperationType;
import io.github.gergilcan.wirej.core.PagedResult;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;

/**
 * Same standard CRUD surface as {@link StandardRepository}, except
 * {@code getAll} always returns a {@link PagedResult} (the page of data plus
 * the unpaginated total count matching the filters) instead of a plain
 * array. Use this instead of {@code StandardRepository} when every consumer
 * of the list endpoint needs the total count (e.g. for "page X of Y" UIs);
 * use {@code StandardRepository} - with its separate {@code count}/
 * {@code getPage} - when most callers just want the array and only some need
 * the count.
 *
 * See {@link StandardRepository} for the table/primary-key resolution rules,
 * the {@code update} partial-update/injection-safety rules, and the
 * {@code createBatch}/{@code updateBatch} batching behavior, all identical
 * here.
 */
public interface PagedRepository<T, ID> extends StandardRepository<T, ID> {
  @StandardOperation(StandardOperationType.GET_PAGE)
  PagedResult<T> getAll(RequestFilters filters, RequestPagination pagination);
}
