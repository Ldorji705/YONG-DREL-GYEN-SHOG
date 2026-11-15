package com.yongdrel.reportingservice.adapter.out.persistence;

import com.yongdrel.reportingservice.application.model.PageResult;
import com.yongdrel.reportingservice.application.port.out.ReportQueryPort;
import com.yongdrel.reportingservice.domain.model.*;
import com.yongdrel.reportingservice.domain.model.Summaries.*;
import com.yongdrel.reportingservice.domain.service.ReportCalculator.OrderRow;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
public class JpaReportQuery implements ReportQueryPort {
    private final EntityManager em;
    public JpaReportQuery(EntityManager em){this.em=em;}
    public Map<EventStatus,Long> eventCountsByStatus(ReportScope s){var q=em.createQuery("select e.status,count(e) from EventEntity e where "+eventFilter(s)+" group by e.status",Object[].class);bindEvent(q,s);Map<EventStatus,Long> out=new EnumMap<>(EventStatus.class);q.getResultList().forEach(r->out.put((EventStatus)r[0],(Long)r[1]));return out;}
    public List<StatusTotals> orderTotals(ReportScope s){var q=em.createQuery("select o.status,count(o),coalesce(sum(o.quantity),0),coalesce(sum(o.amount),0) from OrderEntity o where "+orderFilter(s)+" group by o.status",Object[].class);bindOrder(q,s);return q.getResultList().stream().map(r->new StatusTotals((OrderStatus)r[0],((Number)r[1]).longValue(),((Number)r[2]).longValue(),(BigDecimal)r[3])).toList();}
    public List<PrizeTier> prizes(ReportScope s){var q=em.createQuery("select p.id,p.tier,p.name,p.quantity,p.unitValue from PrizeEntity p where "+prizeFilter(s)+" order by p.tier,p.id",Object[].class);bindPrize(q,s);return q.getResultList().stream().map(r->new PrizeTier((Long)r[0],(Integer)r[1],(String)r[2],((Number)r[3]).intValue(),(BigDecimal)r[4],((BigDecimal)r[4]).multiply(BigDecimal.valueOf(((Number)r[3]).longValue())))).toList();}
    public List<PayoutTotals> payoutTotals(ReportScope s){var q=em.createQuery("select p.status,count(p),coalesce(sum(p.amount),0) from PayoutEntity p where "+payoutFilter(s)+" group by p.status",Object[].class);bindPayout(q,s);return q.getResultList().stream().map(r->new PayoutTotals((PayoutStatus)r[0],((Number)r[1]).longValue(),(BigDecimal)r[2])).toList();}
    public long capacity(ReportScope s){var q=em.createQuery("select coalesce(sum(e.totalTickets),0) from EventEntity e where "+eventFilter(s),Long.class);bindEvent(q,s);return q.getSingleResult();}
    public PageResult<EventProjection> events(Long tenantId,EventStatus status,int page,int size){String w="(:tenant is null or e.tenantId=:tenant) and (:status is null or e.status=:status)";var q=em.createQuery("select e from EventEntity e where "+w+" order by e.id",EventEntity.class).setParameter("tenant",tenantId).setParameter("status",status).setFirstResult(page*size).setMaxResults(size);var content=q.getResultList().stream().map(EventEntity::domain).toList();long total=em.createQuery("select count(e) from EventEntity e where "+w,Long.class).setParameter("tenant",tenantId).setParameter("status",status).getSingleResult();return new PageResult<>(content,page,size,total);}
    public Map<Long,StatusTotals> confirmedTotalsByEvent(ReportScope s){var q=em.createQuery("select o.eventId,count(o),coalesce(sum(o.quantity),0),coalesce(sum(o.amount),0) from OrderEntity o where o.status=:status and "+orderFilter(s)+" group by o.eventId",Object[].class).setParameter("status",OrderStatus.CONFIRMED);bindOrder(q,s);Map<Long,StatusTotals> out=new HashMap<>();q.getResultList().forEach(r->out.put((Long)r[0],new StatusTotals(OrderStatus.CONFIRMED,((Number)r[1]).longValue(),((Number)r[2]).longValue(),(BigDecimal)r[3])));return out;}
    public Map<Long,StatusTotals> confirmedTotalsByEvent(Collection<Long> ids){if(ids.isEmpty())return Map.of();var q=em.createQuery("select o.eventId,count(o),coalesce(sum(o.quantity),0),coalesce(sum(o.amount),0) from OrderEntity o where o.status=:status and o.eventId in :ids group by o.eventId",Object[].class).setParameter("status",OrderStatus.CONFIRMED).setParameter("ids",ids);Map<Long,StatusTotals> out=new HashMap<>();q.getResultList().forEach(r->out.put((Long)r[0],new StatusTotals(OrderStatus.CONFIRMED,((Number)r[1]).longValue(),((Number)r[2]).longValue(),(BigDecimal)r[3])));return out;}
    public Map<Long,Map<OrderStatus,StatusTotals>> buyerTotals(Long buyerId){var rows=em.createQuery("select o.eventId,o.status,count(o),coalesce(sum(o.quantity),0),coalesce(sum(o.amount),0) from OrderEntity o where o.buyerId=:buyer group by o.eventId,o.status",Object[].class).setParameter("buyer",buyerId).getResultList();Map<Long,Map<OrderStatus,StatusTotals>> out=new HashMap<>();rows.forEach(r->out.computeIfAbsent((Long)r[0],k->new EnumMap<>(OrderStatus.class)).put((OrderStatus)r[1],new StatusTotals((OrderStatus)r[1],((Number)r[2]).longValue(),((Number)r[3]).longValue(),(BigDecimal)r[4])));return out;}
    public Map<Long,String> eventNames(Collection<Long> ids){if(ids.isEmpty())return Map.of();var rows=em.createQuery("select e.id,e.name from EventEntity e where e.id in :ids",Object[].class).setParameter("ids",ids).getResultList();Map<Long,String> out=new HashMap<>();rows.forEach(r->out.put((Long)r[0],(String)r[1]));return out;}
    public List<OrderRow> confirmedOrders(ReportScope s){var q=em.createQuery("select o.createdAt,o.quantity,o.amount from OrderEntity o where o.status=:status and "+orderFilter(s)+" order by o.createdAt",Object[].class).setParameter("status",OrderStatus.CONFIRMED);bindOrder(q,s);return q.getResultList().stream().map(r->new OrderRow((java.time.Instant)r[0],((Number)r[1]).intValue(),(BigDecimal)r[2])).toList();}
    private static String eventFilter(ReportScope s){return "(:tenant is null or e.tenantId=:tenant) and (:event is null or e.id=:event)";}
    private static String orderFilter(ReportScope s){return "(:tenant is null or o.tenantId=:tenant) and (:event is null or o.eventId=:event) and (:from is null or o.createdAt>=:from) and (:to is null or o.createdAt<:to)";}
    private static String prizeFilter(ReportScope s){return "(:tenant is null or p.tenantId=:tenant) and (:event is null or p.eventId=:event)";}
    private static String payoutFilter(ReportScope s){return "(:tenant is null or p.tenantId=:tenant) and (:event is null or p.eventId=:event)";}
    private static void bindEvent(TypedQuery<?> q,ReportScope s){q.setParameter("tenant",s.tenantId()).setParameter("event",s.eventId());}
    private static void bindOrder(TypedQuery<?> q,ReportScope s){q.setParameter("tenant",s.tenantId()).setParameter("event",s.eventId()).setParameter("from",s.from()).setParameter("to",s.to());}
    private static void bindPrize(TypedQuery<?> q,ReportScope s){q.setParameter("tenant",s.tenantId()).setParameter("event",s.eventId());}
    private static void bindPayout(TypedQuery<?> q,ReportScope s){q.setParameter("tenant",s.tenantId()).setParameter("event",s.eventId());}
}
