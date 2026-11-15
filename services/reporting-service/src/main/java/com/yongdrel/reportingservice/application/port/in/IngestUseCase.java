package com.yongdrel.reportingservice.application.port.in;

import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.OrderProjection;
import com.yongdrel.reportingservice.domain.model.PayoutProjection;
import com.yongdrel.reportingservice.domain.model.PrizeProjection;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;

/** Keeps the local read model in sync with the owning services. caller == null means the trusted broker path. */
public interface IngestUseCase {

    UpsertOutcome upsertEvent(EventProjection event, Caller caller);

    UpsertOutcome upsertOrder(OrderProjection order, Caller caller);

    UpsertOutcome upsertPrize(PrizeProjection prize, Caller caller);

    UpsertOutcome upsertPayout(PayoutProjection payout, Caller caller);
}
