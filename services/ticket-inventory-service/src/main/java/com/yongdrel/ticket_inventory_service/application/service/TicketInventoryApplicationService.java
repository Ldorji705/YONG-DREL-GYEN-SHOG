package com.yongdrel.ticket_inventory_service.application.service;

import com.yongdrel.ticket_inventory_service.adapter.out.persistence.*;
import com.yongdrel.ticket_inventory_service.application.port.*;
import com.yongdrel.ticket_inventory_service.domain.model.ReservationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant; import java.util.*;

@Service
public class TicketInventoryApplicationService {
    private final InventoryRepository inventories; private final ReservationRepository reservations;
    private final EventOwnershipPort ownership; private final PaymentVerificationPort payments; private final long ttlSeconds;
    public TicketInventoryApplicationService(InventoryRepository inventories, ReservationRepository reservations,
            EventOwnershipPort ownership, PaymentVerificationPort payments,
            @Value("${app.reservation.ttl-seconds:900}") long ttlSeconds) {
        this.inventories=inventories; this.reservations=reservations; this.ownership=ownership; this.payments=payments; this.ttlSeconds=ttlSeconds;
    }
    @Transactional(readOnly=true)
    public Availability availability(UUID eventId, UUID tenantId) {
        requireOwnership(eventId, tenantId);
        InventoryEntity i=inventories.findById(eventId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Inventory not configured"));
        return new Availability(i.getCapacity(), i.getReserved(), i.getCapacity()-i.getReserved());
    }
    @Transactional
    public ReservationEntity reserve(UUID eventId, UUID tenantId, UUID userId, int quantity, String key) {
        if(quantity<1 || key==null || key.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid reservation");
        requireOwnership(eventId, tenantId);
        Optional<ReservationEntity> prior=reservations.findByTenantIdAndIdempotencyKey(tenantId,key);
        if(prior.isPresent()) return prior.get();
        InventoryEntity i=inventories.findForUpdate(eventId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Inventory not configured"));
        if(!i.getTenantId().equals(tenantId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant does not own event");
        if(i.getCapacity()-i.getReserved()<quantity) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient ticket availability");
        i.reserve(quantity); return reservations.save(new ReservationEntity(tenantId,eventId,userId,quantity,key,Instant.now().plusSeconds(ttlSeconds)));
    }
    @Transactional
    public ReservationEntity confirm(UUID id, UUID tenantId, UUID paymentId) {
        ReservationEntity r=getOwned(id,tenantId);
        if(r.getStatus()==ReservationStatus.CONFIRMED) return r;
        if(r.getStatus()!=ReservationStatus.RESERVED || r.getExpiresAt().isBefore(Instant.now())) { releaseInternal(r, ReservationStatus.EXPIRED); throw new ResponseStatusException(HttpStatus.GONE,"Reservation expired"); }
        if(!payments.verify(paymentId,id,tenantId)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment verification unavailable or untrusted");
        r.confirm(paymentId); return r;
    }
    @Transactional
    public void release(UUID id, UUID tenantId) { ReservationEntity r=getOwned(id,tenantId); if(r.getStatus()==ReservationStatus.RESERVED) releaseInternal(r, ReservationStatus.RELEASED); }
    @Transactional public void confirmInternal(UUID id,UUID tenantId,UUID paymentId){ReservationEntity r=getOwned(id,tenantId);if(r.getStatus()==ReservationStatus.CONFIRMED)return;if(r.getStatus()!=ReservationStatus.RESERVED||r.getExpiresAt().isBefore(Instant.now()))throw new ResponseStatusException(HttpStatus.GONE,"Reservation expired");r.confirm(paymentId);}
    @Transactional(readOnly=true) public ReservationEntity internalReservation(UUID id,UUID tenant,UUID user){ReservationEntity r=getOwned(id,tenant);if(!r.getUserId().equals(user))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"User mismatch");return r;}
    @Scheduled(fixedDelayString="${app.reservation.expiry-scan-ms:30000}")
    @Transactional public void expire() { for(ReservationEntity r:reservations.findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED,Instant.now())) releaseInternal(r, ReservationStatus.EXPIRED); }
    private void releaseInternal(ReservationEntity r, ReservationStatus status) {
        inventories.findForUpdate(r.getEventId()).ifPresent(i -> i.release(r.getQuantity()));
        if (status == ReservationStatus.EXPIRED) r.expire(); else r.release();
    }
    private ReservationEntity getOwned(UUID id,UUID tenant) { ReservationEntity r=reservations.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Reservation not found")); if(!r.getTenantId().equals(tenant)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant mismatch"); return r; }
    @Transactional(readOnly=true) public List<ReservationEntity> expired(Instant before){return reservations.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(ReservationStatus.RESERVED,before);}
    private void requireOwnership(UUID event,UUID tenant) { if(!ownership.verify(event,tenant)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Event ownership verification unavailable"); }
    public record Availability(int capacity,int reserved,int available) {}
}
