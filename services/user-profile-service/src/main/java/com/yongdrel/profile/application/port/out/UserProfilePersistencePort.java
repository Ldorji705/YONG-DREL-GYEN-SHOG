package com.yongdrel.profile.application.port.out;

import com.yongdrel.profile.domain.model.UserProfile;
import java.util.Optional;
import java.util.UUID;

public interface UserProfilePersistencePort {

    UserProfile save(UserProfile profile);

    Optional<UserProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}