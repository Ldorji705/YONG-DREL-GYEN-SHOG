package com.yongdrel.notification.application.template;

import com.yongdrel.notification.domain.model.NotificationTemplate;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationTemplateRenderer {

    public String render(
            NotificationTemplate template,
            Map<String, String> variables
    ) {
        if (template == null) {
            throw new IllegalArgumentException(
                    "Notification template is required"
            );
        }

        Map<String, String> safeVariables =
                variables == null ? Map.of() : variables;

        return switch (template) {
            case TICKET_PURCHASED ->
                    "Your ticket purchase was successful. Event: "
                            + value(safeVariables, "eventName")
                            + ". Order ID: "
                            + value(safeVariables, "orderId");

            case DRAW_COMPLETED ->
                    "The draw has been completed for event: "
                            + value(safeVariables, "eventName")
                            + ". Thank you for participating.";

            case WINNER_ANNOUNCED ->
                    "Congratulations! A winner has been announced for event: "
                            + value(safeVariables, "eventName")
                            + ". Please check your account for details.";
        };
    }

    private String value(
            Map<String, String> variables,
            String key
    ) {
        return variables.getOrDefault(key, "Not available");
    }
}
