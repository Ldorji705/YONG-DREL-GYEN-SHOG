# Fairness Verification Function

Invoke `verifyLotteryDraw` with draw and published evidence fields:
`drawId`, `eventId`, `seedCommitment`, `revealedSeed`, `randomnessEvidence`,
`ticketSnapshotHash`, ordered `ticketIds`, `algorithmVersion`, and `winnerIds`.
The result contains `VERIFIED`, `FAILED`, or `INSUFFICIENT_EVIDENCE` and reasons.

For `SHA256_COUNTER_FISHER_YATES_V1`, `ticketIds` must be sorted by UUID string
and hashed as UTF-8 IDs joined with LF. The revealed seed is 32 random bytes
encoded as lowercase hexadecimal; its commitment is SHA-256 of that UTF-8
hexadecimal string. Fisher-Yates uses SHA-256(seed bytes concatenated with an
8-byte big-endian counter), consuming an unbiased rejection-sampled uint32 for
each swap.

Deployment requires Java 17 and a serverless Spring Cloud Function adapter.
Historical draws without the persisted evidence fields remain
`INSUFFICIENT_EVIDENCE`.
