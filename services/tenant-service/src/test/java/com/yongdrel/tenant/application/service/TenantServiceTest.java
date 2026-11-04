
package com.yongdrel.tenant.application.service;

import com.yongdrel.tenant.adapters.out.persistence.TenantRepository;
import com.yongdrel.tenant.application.exception.TenantNotFoundException;
import com.yongdrel.tenant.domain.model.Tenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantService tenantService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void createTenantSuccessfully() {

        when(tenantRepository.existsByNameIgnoreCaseAndOwnerUserId(
                "Test Tenant", ownerId)).thenReturn(false);

        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.createTenant(
                " Test Tenant ",
                ownerId
        );

        assertEquals("Test Tenant", result.getName());
        assertEquals(ownerId, result.getOwnerUserId());
        assertEquals("ACTIVE", result.getStatus());

        verify(tenantRepository).save(any(Tenant.class));
    }

    @Test
    void createTenantRejectsBlankName() {

        assertThrows(
                IllegalArgumentException.class,
                () -> tenantService.createTenant(" ", ownerId)
        );

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void createTenantRejectsDuplicateName() {

        when(tenantRepository.existsByNameIgnoreCaseAndOwnerUserId(
                "Test Tenant", ownerId)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> tenantService.createTenant("Test Tenant", ownerId)
        );

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void getTenantSuccessfullyForOwner() {

        Tenant tenant = createTestTenant();

        when(tenantRepository.findByTenantIdAndOwnerUserId(
                tenantId, ownerId)).thenReturn(Optional.of(tenant));

        Tenant result = tenantService.getTenant(tenantId, ownerId);

        assertEquals(tenantId, result.getTenantId());
        assertEquals(ownerId, result.getOwnerUserId());
    }

    @Test
    void getTenantRejectsDifferentOwner() {

        when(tenantRepository.findByTenantIdAndOwnerUserId(
                tenantId, ownerId)).thenReturn(Optional.empty());

        assertThrows(
                TenantNotFoundException.class,
                () -> tenantService.getTenant(tenantId, ownerId)
        );
    }

    @Test
    void getMyTenantsReturnsOnlyRepositoryResults() {

        List<Tenant> tenants = List.of(createTestTenant());

        when(tenantRepository.findAllByOwnerUserId(ownerId))
                .thenReturn(tenants);

        List<Tenant> result = tenantService.getMyTenants(ownerId);

        assertEquals(1, result.size());
        assertEquals(ownerId, result.get(0).getOwnerUserId());

        verify(tenantRepository).findAllByOwnerUserId(ownerId);
    }

    @Test
    void updateTenantSuccessfully() {

        Tenant tenant = createTestTenant();

        when(tenantRepository.findByTenantIdAndOwnerUserId(
                tenantId, ownerId)).thenReturn(Optional.of(tenant));

        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.updateTenant(
                tenantId,
                "Updated Tenant",
                ownerId
        );

        assertEquals("Updated Tenant", result.getName());

        verify(tenantRepository).save(tenant);
    }

    @Test
    void updateTenantRejectsBlankName() {

        Tenant tenant = createTestTenant();

        when(tenantRepository.findByTenantIdAndOwnerUserId(
                tenantId, ownerId)).thenReturn(Optional.of(tenant));

        assertThrows(
                IllegalArgumentException.class,
                () -> tenantService.updateTenant(tenantId, " ", ownerId)
        );

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void updateTenantRejectsDuplicateName() {

        Tenant tenant = createTestTenant();

        when(tenantRepository.findByTenantIdAndOwnerUserId(
                tenantId, ownerId)).thenReturn(Optional.of(tenant));

        when(tenantRepository.existsByNameIgnoreCaseAndOwnerUserId(
                "Another Tenant", ownerId)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> tenantService.updateTenant(
                        tenantId,
                        "Another Tenant",
                        ownerId
                )
        );

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void updateTenantStatusSuccessfully() {

        Tenant tenant = createTestTenant();

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        when(tenantRepository.save(any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.updateTenantStatus(
                tenantId,
                "inactive"
        );

        assertEquals("INACTIVE", result.getStatus());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void updateTenantStatusRejectsInvalidStatus() {

        assertThrows(
                IllegalArgumentException.class,
                () -> tenantService.updateTenantStatus(
                        tenantId,
                        "INVALID"
                )
        );

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void updateTenantStatusRejectsMissingTenant() {

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.empty());

        assertThrows(
                TenantNotFoundException.class,
                () -> tenantService.updateTenantStatus(
                        tenantId,
                        "ACTIVE"
                )
        );
    }

    private Tenant createTestTenant() {

        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName("Test Tenant");
        tenant.setOwnerUserId(ownerId);
        tenant.setStatus("ACTIVE");

        return tenant;
    }
}
