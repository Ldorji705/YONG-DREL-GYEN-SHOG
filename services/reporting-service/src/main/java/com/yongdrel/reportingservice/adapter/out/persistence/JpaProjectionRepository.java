package com.yongdrel.reportingservice.adapter.out.persistence;

import com.yongdrel.reportingservice.application.port.out.ProjectionRepositoryPort;
import com.yongdrel.reportingservice.domain.model.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JpaProjectionRepository implements ProjectionRepositoryPort {
    private final EntityManager em;
    public JpaProjectionRepository(EntityManager em){this.em=em;}
    public Optional<EventProjection> findEvent(Long id){return Optional.ofNullable(em.find(EventEntity.class,id)).map(EventEntity::domain);}
    public Optional<OrderProjection> findOrder(Long id){return Optional.ofNullable(em.find(OrderEntity.class,id)).map(OrderEntity::domain);}
    public Optional<PrizeProjection> findPrize(Long id){return Optional.ofNullable(em.find(PrizeEntity.class,id)).map(PrizeEntity::domain);}
    public Optional<PayoutProjection> findPayout(Long id){return Optional.ofNullable(em.find(PayoutEntity.class,id)).map(PayoutEntity::domain);}
    public EventProjection save(EventProjection v){return em.merge(EventEntity.from(v)).domain();}
    public OrderProjection save(OrderProjection v){return em.merge(OrderEntity.from(v)).domain();}
    public PrizeProjection save(PrizeProjection v){return em.merge(PrizeEntity.from(v)).domain();}
    public PayoutProjection save(PayoutProjection v){return em.merge(PayoutEntity.from(v)).domain();}
}
