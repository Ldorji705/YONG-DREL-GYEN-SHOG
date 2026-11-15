package com.yongdrel.auditlogservice.config;

import com.yongdrel.auditlogservice.application.port.out.SourceServiceRegistryPort;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

@Component
public class ConfiguredSourceServiceRegistry implements SourceServiceRegistryPort {

    private final Set<String> known;

    public ConfiguredSourceServiceRegistry(AuditProperties properties) {
        this.known = Set.copyOf(new TreeSet<>(properties.knownSources()));
    }

    @Override
    public boolean isKnown(String serviceName) {
        return serviceName != null && known.contains(serviceName);
    }

    @Override
    public Set<String> knownServices() {
        return known;
    }
}
