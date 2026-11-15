package com.yongdrel.reportingservice.application.port.out;

import com.yongdrel.reportingservice.application.model.PageResult;
import com.yongdrel.reportingservice.domain.model.*;
import com.yongdrel.reportingservice.domain.model.Summaries.*;
import com.yongdrel.reportingservice.domain.service.ReportCalculator.OrderRow;
import java.util.*;

public interface ReportQueryPort {
    Map<EventStatus, Long> eventCountsByStatus(ReportScope scope);
    List<StatusTotals> orderTotals(ReportScope scope);
    List<PrizeTier> prizes(ReportScope scope);
    List<PayoutTotals> payoutTotals(ReportScope scope);
    long capacity(ReportScope scope);
    PageResult<EventProjection> events(Long tenantId, EventStatus status, int page, int size);
    Map<Long, StatusTotals> confirmedTotalsByEvent(ReportScope scope);
    default Map<Long, StatusTotals> confirmedTotalsByEvent(Collection<Long> eventIds) {
        return confirmedTotalsByEvent(new ReportScope(null, null, null, null));
    }
    Map<Long, Map<OrderStatus, StatusTotals>> buyerTotals(Long buyerId);
    Map<Long, String> eventNames(Collection<Long> ids);
    List<OrderRow> confirmedOrders(ReportScope scope);
}
