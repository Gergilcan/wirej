package io.github.gergilcan.wirej.core.repositories;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.core.RequestFilters;

public interface CrudRepository<T> {
    @QueryFile("/queries/{entity}/findById.sql")
    T findById(Long id);

    @QueryFile("/queries/{entity}/findAll.sql")
    T[] findAll();

    @QueryFile("/queries/{entity}/findByFilters.sql")
    T[] findByFilters(RequestFilters filters);

    @QueryFile("/queries/{entity}/create.sql")
    void create(T newEntity);

    @QueryFile(value = "/queries/{entity}/delete.sql")
    void delete(Long id);

    @QueryFile("/queries/{entity}/count.sql")
    Long count();

    @QueryFile("/queries/{entity}/countByFilters.sql")
    Long countByFilters(RequestFilters filters);
}