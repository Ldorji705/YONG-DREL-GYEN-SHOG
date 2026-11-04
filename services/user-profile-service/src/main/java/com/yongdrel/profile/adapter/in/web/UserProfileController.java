package com.yongdrel.profile.adapter.in.web;

import com.yongdrel.profile.adapter.in.web.dto.UserProfileRequest;
import com.yongdrel.profile.application.service.UserProfileApplicationService;
import com.yongdrel.profile.domain.model.UserProfile;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/profiles")
public class UserProfileController {

    private final UserProfileApplicationService profileService;

    public UserProfileController(
            UserProfileApplicationService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfile createProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProfileRequest request) {

        UUID userId = resolveUserId(jwt);

        return profileService.createProfile(
                userId,
                request.fullName(),
                request.phoneNumber(),
                request.address()
        );
    }

    @GetMapping("/me")
    public UserProfile getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = resolveUserId(jwt);

        return profileService.getProfile(userId);
    }

    @PutMapping("/me")
    public UserProfile updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProfileRequest request) {

        UUID userId = resolveUserId(jwt);

        return profileService.updateProfile(
                userId,
                request.fullName(),
                request.phoneNumber(),
                request.address()
        );
    }

    private UUID resolveUserId(Jwt jwt) {
        String subject = jwt.getSubject();

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(
                ("yongdrel-auth-user:" + subject)
                    .getBytes(StandardCharsets.UTF_8)
            );
        }
    }

}