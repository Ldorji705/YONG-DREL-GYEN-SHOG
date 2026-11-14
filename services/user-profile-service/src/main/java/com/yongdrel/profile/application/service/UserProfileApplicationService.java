package com.yongdrel.profile.application.service;

import com.yongdrel.profile.application.port.out.UserProfilePersistencePort;
import com.yongdrel.profile.domain.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserProfileApplicationService {

    private final UserProfilePersistencePort persistencePort;

    public UserProfileApplicationService(
            UserProfilePersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    public UserProfile createProfile(
            UUID authenticatedUserId,
            String fullName,
            String phoneNumber,
            String address) {

        if (persistencePort.existsByUserId(authenticatedUserId)) {
            throw new IllegalStateException(
                    "Profile already exists for this user"
            );
        }

        validateFullName(fullName);

        UserProfile profile = new UserProfile();

        profile.setUserId(authenticatedUserId);
        profile.setFullName(fullName.trim());
        profile.setPhoneNumber(phoneNumber);
        profile.setAddress(address);

        return persistencePort.save(profile);
    }

    public UserProfile getProfile(UUID authenticatedUserId) {

        return persistencePort.findByUserId(authenticatedUserId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User profile not found"
                        ));
    }

    public UserProfile updateProfile(
            UUID authenticatedUserId,
            String fullName,
            String phoneNumber,
            String address) {

        UserProfile profile = getProfile(authenticatedUserId);

        validateFullName(fullName);

        profile.setFullName(fullName.trim());
        profile.setPhoneNumber(phoneNumber);
        profile.setAddress(address);

        return persistencePort.save(profile);
    }

    private void validateFullName(String fullName) {

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException(
                    "Full name is required"
            );
        }

        if (fullName.length() > 150) {
            throw new IllegalArgumentException(
                    "Full name must not exceed 150 characters"
            );
        }
    }
}