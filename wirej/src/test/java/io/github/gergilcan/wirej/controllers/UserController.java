package io.github.gergilcan.wirej.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gergilcan.wirej.annotations.ServiceClass;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.rest.StandardRestRepository;
import io.github.gergilcan.wirej.services.CrudUserService;

@RestController
@RequestMapping("/users-generic")
@ServiceClass(CrudUserService.class)
public interface UserController extends StandardRestRepository<User, Long> {
}
