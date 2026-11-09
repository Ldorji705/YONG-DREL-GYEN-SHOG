
package com.yongdrel.servicetemplate.adapter.out.persistence;

import com.yongdrel.servicetemplate.application.port.out.UserPersistencePort;
import com.yongdrel.servicetemplate.domain.model.User;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UserPersistenceAdapter implements UserPersistencePort {

    private final SpringDataUserRepository repository;

    public UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {

        UserEntity entity = new UserEntity(
                user.getId(),
                user.getName(),
                user.getEmail()
        );

        UserEntity saved = repository.save(entity);

        return toDomain(saved);
    }

    @Override
    public List<User> findAll() {

        return repository.findAll()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<User> findById(Long id) {

        return repository.findById(id)
                .map(this::toDomain);
    }

    private User toDomain(UserEntity entity) {

        return new User(
                entity.getId(),
                entity.getName(),
                entity.getEmail()
        );
    }
}

