package com.yongdrel.profile.adapter.out.persistence;

import com.yongdrel.profile.application.port.out.UserProfilePersistencePort;
import com.yongdrel.profile.domain.model.UserProfile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserProfilePersistenceAdapter
        implements UserProfilePersistencePort {

    private final SpringDataUserProfileRepository repository;

    public UserProfilePersistenceAdapter(
            SpringDataUserProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserProfile save(UserProfile profile) {
        UserProfileEntity entity;

        if (profile.getId() != null) {
            entity = repository.findById(profile.getId())
                    .orElseThrow(() ->
                            new IllegalStateException("Profile not found"));
        } else {
            entity = new UserProfileEntity();
            entity.setUserId(profile.getUserId());
        }

        entity.setFullName(profile.getFullName());
        entity.setPhoneNumber(profile.getPhoneNumber());
        entity.setAddress(profile.getAddress());

        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<UserProfile> findByUserId(UUID userId) {
        return repository.findByUserId(userId)
                .map(this::toDomain);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return repository.existsByUserId(userId);
    }

    private UserProfile toDomain(UserProfileEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getUserId(),
                entity.getFullName(),
                entity.getPhoneNumber(),
                entity.getAddress(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}