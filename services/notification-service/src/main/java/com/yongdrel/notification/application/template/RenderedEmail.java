package com.yongdrel.notification.application.template;

public record RenderedEmail(String subject, String htmlBody, String plainTextBody) {
    public RenderedEmail {
        if (subject == null || subject.isBlank()
                || htmlBody == null || htmlBody.isBlank()
                || plainTextBody == null || plainTextBody.isBlank()) {
            throw new IllegalArgumentException("Template function returned incomplete email content");
        }
    }
}
