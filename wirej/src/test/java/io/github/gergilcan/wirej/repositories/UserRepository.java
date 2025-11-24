package io.github.gergilcan.wirej.repositories;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.core.repositories.PagedCrudRepository;
import io.github.gergilcan.wirej.entities.User;

@Repository
public interface UserRepository extends PagedCrudRepository<User> {
    @QueryFile(value = "/queries/User/delete.sql", isBatch = true)
    void delete(@JsonAlias("id") Long[] ids);

    @QueryFile("/queries/User/createBySpecificId.sql")
    void create(Long specificId, User newUser);
}
