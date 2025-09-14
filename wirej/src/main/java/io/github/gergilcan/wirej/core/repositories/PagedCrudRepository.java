package io.github.gergilcan.wirej.core.repositories;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;

public interface PagedCrudRepository<T> extends CrudRepository<T> {
    @QueryFile("/queries/{entity}/findByFiltersPaged.sql")
    T[] findByFilters(RequestFilters filters, RequestPagination pagination);
}