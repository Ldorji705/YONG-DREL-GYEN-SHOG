package com.yongdrel.notification.application.template;

import com.yongdrel.notification.domain.model.NotificationTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Component
public class NotificationTemplateRenderer {
    private final RestClient client;

    public NotificationTemplateRenderer(
            RestClient.Builder builder,
            @Value("${app.services.email-template-url}") String functionUrl) {
        if (functionUrl == null || functionUrl.isBlank()) {
            throw new IllegalStateException("Email template function URL is required");
        }
        client = builder.baseUrl(functionUrl).build();
    }

    public RenderedEmail render(
            NotificationTemplate template,
            Map<String, String> variables,
            UUID recipientId) {
        if (template == null || recipientId == null) {
            throw new IllegalArgumentException("Template and recipient are required");
        }
        try {
            EmailTemplateResponse response = client.post()
                    .body(new EmailTemplateRequest(
                            template.name(), recipientId.toString(), "en", variables))
                    .retrieve()
                    .body(EmailTemplateResponse.class);
            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Template function returned no content");
            }
            return new RenderedEmail(
                    response.subject(), response.htmlBody(), response.plainTextBody());
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Email template function unavailable", exception);
        }
    }

    private record EmailTemplateRequest(
            String templateType, String recipientName, String locale,
            Map<String, String> variables) {}

    private record EmailTemplateResponse(
            String subject, String htmlBody, String plainTextBody) {}
}
