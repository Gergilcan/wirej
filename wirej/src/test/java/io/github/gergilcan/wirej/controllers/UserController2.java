package io.github.gergilcan.wirej.controllers;

import org.springframework.http.ResponseEntity;
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
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.services.UsersService;

@RestController
@RequestMapping("/users2")
@ServiceClass(UsersService.class)
public interface UserController2 {
    @GetMapping("/{id}")
    @ServiceMethod // Will automatically use "getUserById" method name
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<?> getUserById(@PathVariable Long id);

    @PostMapping("/create")
    @ServiceMethod("create") // Explicitly uses "create" method name
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<?> createUser(@RequestBody User newUser);
}
