package io.github.gergilcan.wirej.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.annotations.ServiceMethod;
import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.services.UsersService;

@RestController
@RequestMapping("/users2")
@ServiceClass(UsersService.class)
public interface UserController2 {
    @GetMapping("/{id}")
    @ServiceMethod // Will automatically use "getUserById" method name
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> getUserById(@PathVariable("id") Long id);

    @GetMapping("/")
    @ServiceMethod
    public ResponseEntity<?> getFiltered(RequestFilters filters, RequestPagination pagination);

    @PostMapping("/create")
    @ServiceMethod("create") // Explicitly uses "create" method name
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> createUser(@RequestBody User newUser);

    @DeleteMapping("/{id}")
    @ServiceMethod("delete") // Explicitly uses "delete" method name
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<?> deleteUser(@PathVariable("id") Long id);

    @GetMapping("/count")
    @ServiceMethod("countByFilters")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> countByFilters(RequestFilters filters);
}
