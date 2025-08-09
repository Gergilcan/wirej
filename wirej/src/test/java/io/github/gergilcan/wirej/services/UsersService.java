package io.github.gergilcan.wirej.services;

import org.springframework.stereotype.Service;

import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.repositories.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsersService {
    private final UserRepository userRepository;

    public User getUserById(Long id) {
        return userRepository.findById(id);
    }

    public void create(User newUser) {
        userRepository.create(newUser);
    }
}
