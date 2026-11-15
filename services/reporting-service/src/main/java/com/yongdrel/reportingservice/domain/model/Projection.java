package com.yongdrel.reportingservice.domain.model;

import java.time.Instant;

/** A local, read-only copy of another service's entity, versioned by the source's update time. */
public interface Projection {

    Long id();

    Long tenantId();

    Instant sourceUpdatedAt();
}
