# Notification Service

Set `EMAIL_TEMPLATE_FUNCTION_URL` to the deployed Spring Cloud Function
endpoint for `renderEmailTemplate`. Notification sends
`templateType`, recipient name/identifier, locale, and trusted variables, then
delivers the returned subject, HTML body, and plain-text body.

Template rendering failures fail the notification without delivery. Existing
event/template/channel uniqueness prevents redelivery from sending duplicates.
