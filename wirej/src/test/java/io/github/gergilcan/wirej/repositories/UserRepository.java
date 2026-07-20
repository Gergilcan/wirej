package io.github.gergilcan.wirej.repositories;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.annotations.QueryOperation;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.User;

@Repository
public interface UserRepository {
    @QueryFile("/queries/User/findById.sql")
    User findById(Long id);

    @QueryFile("/queries/User/findById.sql")
    User retrieveAccountsSummaryAuto(Long id);

    @QueryFile(value = "/queries/User/findById.sql", operation = QueryOperation.SELECT)
    User retrieveAccountsSummary(Long id);

    @QueryFile(value = "/queries/User/countByFilters.sql", operation = QueryOperation.COUNT)
    Long tallyUsers(RequestFilters filters);

    @QueryFile(value = "/queries/User/countByFilters.sql", operation = QueryOperation.COUNT)
    Long tallyUsersWithEntityClass(RequestFilters filters, Class<User> entityClass);

    @QueryFile("/queries/User/create.sql")
    void create(User newUser);

    @QueryFile(value = "/queries/User/delete.sql", isBatch = true)
    void delete(@JsonAlias("id") Long[] ids);

    @QueryFile("/queries/User/countByFilters.sql")
    Long countByFilters(RequestFilters filters, Class<User> entityClass);

    @QueryFile("/queries/User/findByFilters.sql")
    User[] findByFilters(RequestFilters filters, RequestPagination pagination);

    @QueryFile("/queries/User/findByIdWithMinAge.sql")
    User findByIdWithMinAge(Long id, int minAge, RequestPagination page);
}
