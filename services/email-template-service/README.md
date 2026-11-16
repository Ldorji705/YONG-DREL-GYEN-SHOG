# Email Template Function

Invoke `renderEmailTemplate` with `templateType`, `recipientName`, `locale`,
and a map of validated template variables. It returns `subject`, `htmlBody`,
and `plainTextBody`. Notification Service remains responsible for delivery.

Deploy with Java 17 and a Spring Cloud Function serverless adapter. No SMTP,
secrets, tokens, or delivery credentials are required.
