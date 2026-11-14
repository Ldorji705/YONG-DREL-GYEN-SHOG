package com.yongdrel.reportingservice.application.port.out;

import com.yongdrel.reportingservice.domain.model.*;
import java.util.Optional;

public interface ProjectionRepositoryPort {
    Optional<EventProjection> findEvent(Long id);
    Optional<OrderProjection> findOrder(Long id);
    Optional<PrizeProjection> findPrize(Long id);
    Optional<PayoutProjection> findPayout(Long id);
    EventProjection save(EventProjection value);
    OrderProjection save(OrderProjection value);
    PrizeProjection save(PrizeProjection value);
    PayoutProjection save(PayoutProjection value);
}
