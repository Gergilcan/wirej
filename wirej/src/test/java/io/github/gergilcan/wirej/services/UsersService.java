package io.github.gergilcan.wirej.services;

import org.springframework.stereotype.Service;

import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
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

    public User[] getFiltered(RequestFilters filters, RequestPagination pagination) {
        // Assuming there's a method in UserRepository to handle filters
        return userRepository.findByFilters(filters, pagination);
    }

    public void create(User newUser) {
        userRepository.create(newUser);
    }

    public void delete(Long id) {
        userRepository.delete(new Long[] { id }); // Assuming the delete method is implemented to handle this
    }
}
