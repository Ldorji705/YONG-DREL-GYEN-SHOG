package com.yongdrel.auditlogservice.application.port.out;

public interface TenantReferencePort {
    boolean isEnabled();
    boolean tenantExists(Long tenantId);
}
