package com.yongdrel.auditlogservice.domain;

import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import com.yongdrel.auditlogservice.domain.service.IntegrityHasher;

import org.junit.jupiter.api.Test;

import static com.yongdrel.auditlogservice.TestData.NOW;
import static com.yongdrel.auditlogservice.TestData.draft;
import static org.assertj.core.api.Assertions.assertThat;

class IntegrityHasherTest {

    @Test
    void hashIsDeterministicSha256Hex() {
        AuditEvent e = AuditEvent.create(draft(), NOW);
        assertThat(IntegrityHasher.hash(e)).isEqualTo(IntegrityHasher.hash(e)).matches("^[0-9a-f]{64}$");
    }

    @Test
    void nullFieldsAreHashedDistinctlyFromEmptyStrings() {
        AuditEvent withNulls = new AuditEvent(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        AuditEvent withEmpty = new AuditEvent(null, null, null, null, null, null, "", null, null, null, null,
                null, null, null, null, null);

        assertThat(IntegrityHasher.hash(withNulls)).hasSize(64).isNotEqualTo(IntegrityHasher.hash(withEmpty));
    }

    @Test
    void movingAValueBetweenFieldsChangesTheHash() {
        AuditEvent a = new AuditEvent(null, null, null, null, null, null, "AB", null, null, "C", null,
                null, null, null, null, null);
        AuditEvent b = new AuditEvent(null, null, null, null, null, null, "A", null, null, "BC", null,
                null, null, null, null, null);
        assertThat(IntegrityHasher.hash(a)).isNotEqualTo(IntegrityHasher.hash(b));
    }
}
