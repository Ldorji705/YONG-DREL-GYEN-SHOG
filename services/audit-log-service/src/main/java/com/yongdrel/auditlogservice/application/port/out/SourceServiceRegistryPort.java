package com.yongdrel.auditlogservice.application.port.out;

import java.util.Set;

public interface SourceServiceRegistryPort {
    boolean isKnown(String sourceService);
    Set<String> knownServices();
}
