package io.github.gergilcan.wirej.repository;

import io.github.gergilcan.wirej.annotations.StandardOperation;
import io.github.gergilcan.wirej.annotations.StandardOperationType;
import io.github.gergilcan.wirej.core.PagedResult;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;

/**
 * Extends {@link StandardRepository} with a paginated {@code getAll} overload:
 * {@code getAll(filters, pagination)} returns a {@link PagedResult} (the page
 * of data plus the unpaginated total count matching the filters) in a single
 * round trip, on top of the inherited unpaginated {@code getAll(filters)} and
 * the rest of the CRUD surface. Use this when a caller needs a page of data
 * together with the total count (e.g. for "page X of Y" UIs); a plain
 * {@link StandardRepository} covers the cases that only need the array, or the
 * count alone via {@code count}.
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
