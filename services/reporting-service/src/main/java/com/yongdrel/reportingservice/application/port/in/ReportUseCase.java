package com.yongdrel.reportingservice.application.port.in;

import com.yongdrel.reportingservice.application.model.PageResult;
import com.yongdrel.reportingservice.application.model.ReportRequest;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.Summaries;

public interface ReportUseCase {

    Summaries.Overview overview(ReportRequest request, Caller caller);

    PageResult<Summaries.EventRow> events(Long tenantId, EventStatus status, int page, int size, Caller caller);

    Summaries.EventSummary eventSummary(Long eventId, Caller caller);

    Summaries.SalesSummary sales(ReportRequest request, Caller caller);

    Summaries.TicketSummary tickets(ReportRequest request, Caller caller);

    Summaries.PrizeSummary prizes(ReportRequest request, Caller caller);

    Summaries.PayoutSummary payouts(ReportRequest request, Caller caller);

    Summaries.MyPurchases myPurchases(Caller caller);
}
