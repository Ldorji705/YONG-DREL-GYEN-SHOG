# Payment QR Function

Invoke the `generatePaymentQr` Spring Cloud Function with JSON containing
`tenantId`, `organizerId`, `orderId`, `amount`, `currency`, and `reference`.
It returns a PNG data URI, QR payload, destination name, instructions, and
order reference. The function retrieves order totals and payment destinations
from trusted internal services; client bank details are never accepted.

Deployment requires Java 17, the order and tenant internal API keys, reachable
trusted upstream services, and a serverless adapter that invokes the Spring
Cloud Function. This QR is a generic manual-transfer payload and does not
claim compatibility with any bank-specific protocol.
