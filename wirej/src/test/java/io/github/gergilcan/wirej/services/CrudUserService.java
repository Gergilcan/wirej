package io.github.gergilcan.wirej.services;

import java.util.Map;

import org.springframework.stereotype.Service;

import io.github.gergilcan.wirej.core.RequestFilters;
import io.github.gergilcan.wirej.core.RequestPagination;
import io.github.gergilcan.wirej.entities.User;
import io.github.gergilcan.wirej.repositories.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrudUserService {
    private final UserRepository userRepository;

    public User get(Long id) {
        return userRepository.findById(id);
    }

    public User[] getAll(RequestFilters filters, RequestPagination pagination) {
        return userRepository.findByFilters(filters, pagination);
    }

    public User create(User entity) {
        userRepository.create(entity);
        return entity;
    }

    public User update(Long id, User entity) {
        // Full replace (PUT): the path id wins over whatever the body carried.
        entity.setId(id);
        return entity;
    }

    public User patch(Long id, Map<String, Object> changes) {
        User user = userRepository.findById(id);
        if (user != null && changes.containsKey("name")) {
            user.setName((String) changes.get("name"));
        }
        return user;
    }

    public void delete(Long id) {
        userRepository.delete(new Long[] { id });
    }
}
