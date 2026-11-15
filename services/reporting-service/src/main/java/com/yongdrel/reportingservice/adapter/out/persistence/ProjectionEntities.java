package com.yongdrel.reportingservice.adapter.out.persistence;

import com.yongdrel.reportingservice.domain.model.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "report_events")
class EventEntity {
    @Id private Long id; @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(nullable=false,length=120) private String name;
    @Column(name="ticket_price",nullable=false,precision=12,scale=2) private BigDecimal ticketPrice;
    @Column(name="total_tickets",nullable=false) private int totalTickets;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EventStatus status;
    @Column(name="sales_close_at") private Instant salesCloseAt;
    @Column(name="source_updated_at",nullable=false) private Instant sourceUpdatedAt;
    protected EventEntity() {}
    static EventEntity from(EventProjection v) { var e=new EventEntity(); e.id=v.id(); e.tenantId=v.tenantId(); e.name=v.name(); e.ticketPrice=v.ticketPrice(); e.totalTickets=v.totalTickets(); e.status=v.status(); e.salesCloseAt=v.salesCloseAt(); e.sourceUpdatedAt=v.sourceUpdatedAt(); return e; }
    EventProjection domain(){return new EventProjection(id,tenantId,name,ticketPrice,totalTickets,status,salesCloseAt,sourceUpdatedAt);}
}

@Entity @Table(name = "report_orders")
class OrderEntity {
    @Id private Long id; @Column(name="event_id",nullable=false) private Long eventId; @Column(name="tenant_id",nullable=false) private Long tenantId; @Column(name="buyer_id",nullable=false) private Long buyerId; @Column(nullable=false) private int quantity; @Column(nullable=false,precision=12,scale=2) private BigDecimal amount; @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private OrderStatus status; @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="confirmed_at") private Instant confirmedAt; @Column(name="source_updated_at",nullable=false) private Instant sourceUpdatedAt;
    protected OrderEntity() {}
    static OrderEntity from(OrderProjection v){var e=new OrderEntity();e.id=v.id();e.eventId=v.eventId();e.tenantId=v.tenantId();e.buyerId=v.buyerId();e.quantity=v.quantity();e.amount=v.amount();e.status=v.status();e.createdAt=v.createdAt();e.confirmedAt=v.confirmedAt();e.sourceUpdatedAt=v.sourceUpdatedAt();return e;}
    OrderProjection domain(){return new OrderProjection(id,eventId,tenantId,buyerId,quantity,amount,status,createdAt,confirmedAt,sourceUpdatedAt);}
}

@Entity @Table(name = "report_prizes")
class PrizeEntity {
    @Id private Long id; @Column(name="event_id",nullable=false) private Long eventId; @Column(name="tenant_id",nullable=false) private Long tenantId; @Column(nullable=false) private int tier; @Column(nullable=false,length=120) private String name; @Column(nullable=false) private int quantity; @Column(name="unit_value",nullable=false,precision=12,scale=2) private BigDecimal unitValue; @Column(name="source_updated_at",nullable=false) private Instant sourceUpdatedAt;
    protected PrizeEntity() {}
    static PrizeEntity from(PrizeProjection v){var e=new PrizeEntity();e.id=v.id();e.eventId=v.eventId();e.tenantId=v.tenantId();e.tier=v.tier();e.name=v.name();e.quantity=v.quantity();e.unitValue=v.unitValue();e.sourceUpdatedAt=v.sourceUpdatedAt();return e;}
    PrizeProjection domain(){return new PrizeProjection(id,eventId,tenantId,tier,name,quantity,unitValue,sourceUpdatedAt);}
}

@Entity @Table(name = "report_payouts")
class PayoutEntity {
    @Id private Long id; @Column(name="event_id",nullable=false) private Long eventId; @Column(name="tenant_id",nullable=false) private Long tenantId; @Column(name="prize_id",nullable=false) private Long prizeId; @Column(name="winner_id",nullable=false) private Long winnerId; @Column(nullable=false,precision=12,scale=2) private BigDecimal amount; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PayoutStatus status; @Column(name="paid_at") private Instant paidAt; @Column(name="source_updated_at",nullable=false) private Instant sourceUpdatedAt;
    protected PayoutEntity() {}
    static PayoutEntity from(PayoutProjection v){var e=new PayoutEntity();e.id=v.id();e.eventId=v.eventId();e.tenantId=v.tenantId();e.prizeId=v.prizeId();e.winnerId=v.winnerId();e.amount=v.amount();e.status=v.status();e.paidAt=v.paidAt();e.sourceUpdatedAt=v.sourceUpdatedAt();return e;}
    PayoutProjection domain(){return new PayoutProjection(id,eventId,tenantId,prizeId,winnerId,amount,status,paidAt,sourceUpdatedAt);}
}
