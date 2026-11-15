package com.yongdrel.reportingservice.domain;

import com.yongdrel.reportingservice.domain.model.Role;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @ParameterizedTest
    @CsvSource({"USER,STUDENT", "student,STUDENT", "ROLE_ORGANIZER,ORGANIZER", "Admin,ADMIN", "SERVICE,SERVICE"})
    void known(String claim, Role role) {
        assertThat(Role.fromClaim(claim)).contains(role);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ROOT", "ROLE_"})
    void unknown(String claim) {
        assertThat(Role.fromClaim(claim)).isEmpty();
    }
}
