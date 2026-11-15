package com.yongdrel.fraudriskservice.application.port.out;

import com.yongdrel.fraudriskservice.application.model.OrderSnapshot;
import java.util.Optional;

public interface OrderReferencePort {
    boolean isEnabled();
    Optional<OrderSnapshot> findOrder(Long orderId);
}
