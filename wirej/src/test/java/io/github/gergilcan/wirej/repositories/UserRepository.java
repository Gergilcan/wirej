package io.github.gergilcan.wirej.repositories;

import org.springframework.stereotype.Repository;

import io.github.gergilcan.wirej.annotations.QueryFile;
import io.github.gergilcan.wirej.entities.User;

@Repository
public interface UserRepository {
    @QueryFile("/queries/User/findById.sql")
    User findById(Long id);

    @QueryFile("/queries/User/create.sql")
    void create(User newUser);

    @QueryFile("/queries/User/findByEmail.sql") // This file doesn't exist - should trigger validation error
    User findByEmail(String email);
}
