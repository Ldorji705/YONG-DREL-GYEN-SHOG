package com.yongdrel.ticket_inventory_service.application.service;

import com.yongdrel.ticket_inventory_service.adapter.out.persistence.*;
import com.yongdrel.ticket_inventory_service.application.port.*;
import com.yongdrel.ticket_inventory_service.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketInventoryApplicationServiceTest {
    @Mock InventoryRepository inventories; @Mock ReservationRepository reservations;
    @Mock EventOwnershipPort ownership; @Mock PaymentVerificationPort payments;
    TicketInventoryApplicationService service;

    @org.junit.jupiter.api.BeforeEach void setUp() {
        service = new TicketInventoryApplicationService(inventories, reservations, ownership, payments, 900);
    }

    @Test void reserveIsIdempotentByTenantAndKey() {
        UUID tenant=UUID.randomUUID(), event=UUID.randomUUID(), user=UUID.randomUUID();
        ReservationEntity prior=new ReservationEntity(tenant,event,user,1,"same",Instant.now().plusSeconds(10));
        when(ownership.verify(event,tenant)).thenReturn(true);
        when(reservations.findByTenantIdAndIdempotencyKey(tenant,"same")).thenReturn(Optional.of(prior));
        assertSame(prior,service.reserve(event,tenant,user,1,"same"));
        verifyNoInteractions(inventories);
    }

    @Test void confirmationFailsClosedWhenPaymentCannotBeVerified() {
        UUID tenant=UUID.randomUUID(), event=UUID.randomUUID(), user=UUID.randomUUID(), id=UUID.randomUUID();
        ReservationEntity reservation=new ReservationEntity(tenant,event,user,1,"key",Instant.now().plusSeconds(100));
        when(reservations.findById(id)).thenReturn(Optional.of(reservation));
        when(payments.verify(any(),eq(id),eq(tenant))).thenReturn(false);
        assertThrows(ResponseStatusException.class,()->service.confirm(id,tenant,UUID.randomUUID()));
        assertEquals(ReservationStatus.RESERVED,reservation.getStatus());
    }
}
