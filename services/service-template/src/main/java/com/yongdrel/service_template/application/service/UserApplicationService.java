
package com.yongdrel.servicetemplate.application.service;

import com.yongdrel.servicetemplate.application.port.in.UserUseCase;
import com.yongdrel.servicetemplate.application.port.out.UserPersistencePort;
import com.yongdrel.servicetemplate.domain.model.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserApplicationService implements UserUseCase {

    private final UserPersistencePort persistencePort;

    public UserApplicationService(UserPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Override
    public User registerUser(User user) {

        if (user.getName() == null || user.getName().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        user.setId(null);

        return persistencePort.save(user);
    }

    @Override
    public List<User> getAllUsers() {
        return persistencePort.findAll();
    }

    @Override
    public User getUserById(Long id) {
        return persistencePort.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }
}
