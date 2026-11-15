package com.yongdrel.auditlogservice.adapter.out.persistence;

import com.yongdrel.auditlogservice.application.model.*;
import com.yongdrel.auditlogservice.application.port.out.AuditEventRepositoryPort;
import com.yongdrel.auditlogservice.domain.model.AuditEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
public class JpaAuditEventRepository implements AuditEventRepositoryPort {
    private final EntityManager entityManager;

    public JpaAuditEventRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditEvent> findByEventId(UUID eventId) {
        return entityManager.createQuery("select e from AuditEventEntity e where e.eventId = :eventId",
                        AuditEventEntity.class).setParameter("eventId", eventId).getResultStream()
                .findFirst().map(AuditEventEntity::toDomain);
    }

    @Override
    public AuditEvent append(AuditEvent event) {
        AuditEventEntity entity = AuditEventEntity.from(event);
        entityManager.persist(entity);
        entityManager.flush();
        return entity.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditEvent> findById(Long id) {
        return Optional.ofNullable(entityManager.find(AuditEventEntity.class, id)).map(AuditEventEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditEvent> search(AuditSearchCriteria criteria, int page, int size) {
        return query(criteria, page, size, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> timeline(AuditSearchCriteria criteria, int limit) {
        return query(criteria, 0, limit, true).content();
    }

    private PageResult<AuditEvent> query(AuditSearchCriteria c, int page, int size, boolean limitOnly) {
        String where = filters(c);
        TypedQuery<AuditEventEntity> query = entityManager.createQuery(
                "select e from AuditEventEntity e" + where + " order by e.occurredAt desc, e.id desc",
                AuditEventEntity.class);
        bind(query, c);
        query.setFirstResult(page * size).setMaxResults(size);
        List<AuditEvent> content = query.getResultList().stream().map(AuditEventEntity::toDomain).toList();
        if (limitOnly) {
            return new PageResult<>(content, 0, size, content.size());
        }
        Long total = entityManager.createQuery("select count(e) from AuditEventEntity e" + where, Long.class)
                .setParameter("tenantId", c.tenantId()).setParameter("actorId", c.actorId())
                .setParameter("actorType", c.actorType()).setParameter("action", c.action())
                .setParameter("outcome", c.outcome()).setParameter("targetType", c.targetType())
                .setParameter("targetId", c.targetId()).setParameter("correlationId", c.correlationId())
                .setParameter("sourceService", c.sourceService()).setParameter("from", c.from())
                .setParameter("to", c.to()).getSingleResult();
        return new PageResult<>(content, page, size, total);
    }

    private static String filters(AuditSearchCriteria c) {
        return " where (:tenantId is null or e.tenantId = :tenantId)"
                + " and (:actorId is null or e.actorId = :actorId)"
                + " and (:actorType is null or e.actorType = :actorType)"
                + " and (:action is null or e.action = :action)"
                + " and (:outcome is null or e.outcome = :outcome)"
                + " and (:targetType is null or e.targetType = :targetType)"
                + " and (:targetId is null or e.targetId = :targetId)"
                + " and (:correlationId is null or e.correlationId = :correlationId)"
                + " and (:sourceService is null or e.sourceService = :sourceService)"
                + " and (:from is null or e.occurredAt >= :from)"
                + " and (:to is null or e.occurredAt < :to)";
    }

    private static void bind(TypedQuery<?> query, AuditSearchCriteria c) {
        query.setParameter("tenantId", c.tenantId()).setParameter("actorId", c.actorId())
                .setParameter("actorType", c.actorType()).setParameter("action", c.action())
                .setParameter("outcome", c.outcome()).setParameter("targetType", c.targetType())
                .setParameter("targetId", c.targetId()).setParameter("correlationId", c.correlationId())
                .setParameter("sourceService", c.sourceService()).setParameter("from", c.from())
                .setParameter("to", c.to());
    }
}
