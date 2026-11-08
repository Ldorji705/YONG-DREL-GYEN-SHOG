package com.yongdrel.ticket_issuance_service.messaging;

import com.yongdrel.ticket_issuance_service.dto.IssueTicketRequest;
import com.yongdrel.ticket_issuance_service.application.service.TicketIssuanceService;
import com.yongdrel.ticket_issuance_service.messaging.event.PaymentConfirmedEvent;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.payment.listener.enabled",
        havingValue = "true"
)
public class PaymentConfirmedListener {

    private final TicketIssuanceService ticketIssuanceService;

    public PaymentConfirmedListener(
            TicketIssuanceService ticketIssuanceService) {

        this.ticketIssuanceService = ticketIssuanceService;
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_CONFIRMED_QUEUE)
    public void handlePaymentConfirmed(PaymentConfirmedEvent event) {

        if (event == null
                || event.eventId() == null
                || event.paymentId() == null
                || event.orderId() == null
                || event.lotteryEventId() == null
                || event.userId() == null
                || event.confirmedAt() == null
                || event.quantity() < 1
                || event.quantity() > 100) {

            throw new IllegalArgumentException(
                    "Invalid payment confirmation event"
            );
        }

        IssueTicketRequest request = new IssueTicketRequest(
                event.orderId(),
                event.lotteryEventId(),
                event.userId(),
                event.quantity()
        );

        ticketIssuanceService.issueTickets(request);
    }
}
