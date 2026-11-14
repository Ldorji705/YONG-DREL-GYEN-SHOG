package com.yongdrel.auditlogservice.adapter.out.tenant;

import com.yongdrel.auditlogservice.application.exception.DependencyUnavailableException;
import com.yongdrel.auditlogservice.application.port.out.TenantReferencePort;
import com.yongdrel.auditlogservice.config.TenantServiceProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TenantServiceReferenceAdapter implements TenantReferencePort {
    private final TenantServiceProperties properties;
    private final RestClient client;

    public TenantServiceReferenceAdapter(TenantServiceProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public boolean tenantExists(Long tenantId) {
        if (!properties.enabled()) {
            return true;
        }
        try {
            HttpStatusCode status = client.get().uri("/api/tenants/{id}", tenantId)
                    .exchange((request, response) -> response.getStatusCode());
            if (status.is2xxSuccessful()) {
                return true;
            }
            if (status.value() == 404) {
                return false;
            }
            throw unavailable("Tenant Service returned HTTP " + status.value());
        } catch (DependencyUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable("Tenant Service is unavailable");
        }
    }

    private static DependencyUnavailableException unavailable(String message) {
        return new DependencyUnavailableException(message);
    }
}
