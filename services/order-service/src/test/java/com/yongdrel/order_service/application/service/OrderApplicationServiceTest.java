package com.yongdrel.order_service.application.service;
import com.yongdrel.order_service.adapter.out.persistence.*;
import com.yongdrel.order_service.application.port.*;
import com.yongdrel.order_service.domain.model.OrderStatus;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.Mock; import org.mockito.junit.jupiter.MockitoExtension; import org.springframework.web.server.ResponseStatusException;
import java.util.*; import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class) class OrderApplicationServiceTest{
 @Mock OrderRepository orders; @Mock OutboxRepository outbox; @Mock TicketInventoryPort inventory; @Mock PaymentTrustPort payments;
 OrderApplicationService service; UUID user=UUID.randomUUID(),tenant=UUID.randomUUID(),event=UUID.randomUUID(),reservation=UUID.randomUUID();
 @BeforeEach void setUp(){service=new OrderApplicationService(orders,outbox,inventory,payments);}
 @Test void createIsIdempotentAndDoesNotReserveTwice(){OrderEntity existing=new OrderEntity(user,tenant,event,reservation);when(orders.findByReservationId(reservation)).thenReturn(Optional.of(existing));assertSame(existing,service.create(user,tenant,reservation));verifyNoInteractions(inventory);}
 @Test void proofAndApprovalAreRequiredForConfirmation(){OrderEntity o=new OrderEntity(user,tenant,event,reservation);when(orders.findById(o.getId())).thenReturn(Optional.of(o));when(payments.verifyProof(o.getId(),tenant,"proof")).thenReturn(true);when(payments.approved(o.getId(),tenant)).thenReturn(true);service.submitProof(o.getId(),tenant,"proof");service.confirm(o.getId(),tenant);assertEquals(OrderStatus.CONFIRMED,o.getStatus());}
 @Test void ownershipIsEnforced(){OrderEntity o=new OrderEntity(user,tenant,event,reservation);when(orders.findById(o.getId())).thenReturn(Optional.of(o));assertThrows(ResponseStatusException.class,()->service.confirm(o.getId(),UUID.randomUUID()));}
 @Test void failureReleasesReservationAndIsIdempotent(){OrderEntity o=new OrderEntity(user,tenant,event,reservation);when(orders.findById(o.getId())).thenReturn(Optional.of(o));service.fail(o.getId(),tenant);service.fail(o.getId(),tenant);assertEquals(OrderStatus.FAILED,o.getStatus());verify(inventory, times(1)).release(reservation,tenant);}
}
