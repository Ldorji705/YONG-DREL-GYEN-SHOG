package com.yongdrel.order_service.application.service;
import com.yongdrel.order_service.adapter.out.persistence.*;
import com.yongdrel.order_service.application.port.*;
import com.yongdrel.order_service.domain.model.OrderStatus;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service public class OrderApplicationService {
 private final OrderRepository orders; private final OutboxRepository outbox; private final TicketInventoryPort inventory; private final PaymentTrustPort payments; private final InventoryPaymentPort inventoryPayment;
 public OrderApplicationService(OrderRepository orders,OutboxRepository outbox,TicketInventoryPort inventory,PaymentTrustPort payments){this(orders,outbox,inventory,payments,null);}
 public OrderApplicationService(OrderRepository orders,OutboxRepository outbox,TicketInventoryPort inventory,PaymentTrustPort payments,InventoryPaymentPort inventoryPayment){this.orders=orders;this.outbox=outbox;this.inventory=inventory;this.payments=payments;this.inventoryPayment=inventoryPayment;}
 @Transactional public OrderEntity create(UUID userId,UUID tenantId,UUID reservationId){
  Optional<OrderEntity> existing=orders.findByReservationId(reservationId); if(existing.isPresent()){ensureOwner(existing.get(),userId,tenantId);return existing.get();}
  TicketInventoryPort.Reservation r=inventory.reservation(reservationId,tenantId,userId);
  if(r==null||!r.active()||!tenantId.equals(r.tenantId())||!userId.equals(r.userId())||r.amount()==null||r.currency()==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Trusted reservation or pricing unavailable");
  OrderEntity o=orders.save(new OrderEntity(userId,tenantId,r.eventId(),reservationId,r.quantity(),r.amount(),r.currency())); event(o,"ORDER_CREATED"); return o;
 }
 @Transactional public OrderEntity submitProof(UUID id,UUID tenantId,String reference){
  OrderEntity o=owned(id,tenantId); if(o.getStatus()==OrderStatus.AWAITING_VERIFICATION)return o;
  if(o.getStatus()!=OrderStatus.AWAITING_PAYMENT||reference==null||reference.isBlank()||!payments.verifyProof(id,tenantId,reference)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Untrusted payment proof");
  o.submitProof(reference); event(o,"ORDER_PAYMENT_SUBMITTED"); return o;
 }
 @Transactional public OrderEntity confirm(UUID id,UUID tenantId){
  OrderEntity o=owned(id,tenantId); if(o.getStatus()==OrderStatus.CONFIRMED)return o;
  if(o.getStatus()!=OrderStatus.AWAITING_VERIFICATION||!payments.approved(id,tenantId)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment approval unavailable");
  o.confirm(); event(o,"ORDER_CONFIRMED"); return o;
 }
 @Transactional public void fail(UUID id,UUID tenantId){OrderEntity o=owned(id,tenantId); if(o.getStatus()==OrderStatus.CONFIRMED||o.getStatus()==OrderStatus.FAILED)return; o.fail();inventory.release(o.getReservationId(),tenantId);event(o,"ORDER_FAILED");}
 @Transactional public void confirmFromPayment(UUID id,UUID tenant,UUID payment){OrderEntity o=owned(id,tenant);if(o.getStatus()==OrderStatus.CONFIRMED)return;if(o.getStatus()!=OrderStatus.AWAITING_VERIFICATION)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order is not awaiting verification");if(inventoryPayment==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Inventory confirmation unavailable");inventoryPayment.confirm(o.getReservationId(),tenant,payment);o.confirm();event(o,"ORDER_CONFIRMED");}
 @Transactional public void failFromPayment(UUID id,UUID tenant){fail(id,tenant);}
 @Transactional public List<OrderEntity> own(UUID user,UUID tenant){return orders.findByUserIdAndTenantId(user,tenant);}
 @Transactional(readOnly=true) public List<OrderEntity> tenant(UUID tenant){return orders.findByTenantId(tenant);}
 @Transactional(readOnly=true) public List<OrderEntity> expired(java.time.Instant before){return orders.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List.of(OrderStatus.AWAITING_PAYMENT,OrderStatus.AWAITING_VERIFICATION),before.minusSeconds(900));}
 @Transactional(readOnly=true) public boolean owns(UUID id,UUID user,UUID tenant){return orders.findById(id).map(o->o.getUserId().equals(user)&&o.getTenantId().equals(tenant)).orElse(false);}
 @Transactional(readOnly=true) public com.yongdrel.order_service.adapter.in.web.InternalOrderController.PaymentDetails paymentDetails(UUID id,UUID tenant){OrderEntity o=owned(id,tenant);return details(o);}
 @Transactional(readOnly=true) public com.yongdrel.order_service.adapter.in.web.InternalOrderController.PaymentDetails paymentDetailsInternal(UUID id){OrderEntity o=orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));return details(o);}
 private com.yongdrel.order_service.adapter.in.web.InternalOrderController.PaymentDetails details(OrderEntity o){return new com.yongdrel.order_service.adapter.in.web.InternalOrderController.PaymentDetails(o.getId(),o.getTenantId(),o.getUserId(),o.getEventId(),o.getQuantity(),o.getStatus().name(),o.getAmount(),o.getCurrency(),o.getId().toString());}
 private OrderEntity owned(UUID id,UUID tenant){OrderEntity o=orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));if(!o.getTenantId().equals(tenant))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant mismatch");return o;}
 private void ensureOwner(OrderEntity o,UUID user,UUID tenant){if(!o.getUserId().equals(user)||!o.getTenantId().equals(tenant))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Order ownership mismatch");}
 private void event(OrderEntity o,String type){outbox.save(new OutboxEventEntity(o.getId(),type,"{\"orderId\":\""+o.getId()+"\",\"status\":\""+o.getStatus()+"\"}"));}
}
